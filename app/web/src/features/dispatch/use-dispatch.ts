import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { assignDeskTask, deskBoardQuery, undoDeskAssign } from '../../api/desk';
import type { DeskTask } from '../../api/types';
import {
  assignFailureNotice,
  assignRequest,
  assumedAssigned,
  undoFailureNotice,
  withTaskReplaced,
  type AssignDept,
  type AssignVia,
} from '../../lib/dispatch';
import { useDispatchStore, type AssignToast } from '../../stores/dispatch';
import { settleConflictOnBoard } from './settle-conflict';

function replaceInBoard(queryClient: QueryClient, task: DeskTask) {
  queryClient.setQueryData(deskBoardQuery.queryKey, (board) => (board ? withTaskReplaced(board, task) : board));
}

/**
 * 後端回的只有這一張單，但頁首的部門張數、今天派出幾張等也跟著變了，
 * 所以每次寫完都整份重讀看板，不靠即時串流補。
 */
function refreshBoard(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: deskBoardQuery.queryKey });
}

/**
 * 還在等後端回應的派單，以單的 id 為鍵，值是「有沒有派成功」。
 * 浮條一出現就能按「復原」，但復原要等這一次派單有結果才送：
 * 先送出去的話，復原可能比派單先到後端而被拒，派單卻接著生效，櫃檯以為收回來了。
 */
const pendingAssigns = new Map<string, Promise<boolean>>();

/**
 * 派單的流程本身，不依賴 React，所以三個入口與單元測試都能直接用。
 * 按下去就先照成功的樣子畫（這張單離開待派單、底部出現浮條），不另外確認，再送給後端。
 * 櫃檯常常一邊講電話一邊派，等後端回來才變畫面會讓人以為沒按到而再按一次。
 * 沒派成功時收掉這張單的浮條，跳對話框說明原因，再重讀看板：
 * 撞單時這張單已經不在待派單，和當場回答撞單一樣照後端說的把它移出待派單（`settleConflictOnBoard`）；
 * 其他失敗（例如斷線）把這張單還原成按下前的樣子，
 * 若連重讀也失敗，單才不會卡在先畫的「待認領」而從待派單消失。
 */
export async function assignTaskFlow(queryClient: QueryClient, task: DeskTask, dept: AssignDept, via: AssignVia) {
  // 正在讀的看板若在派單之後才回來，會把先畫好的樣子蓋回待派單，所以先取消。
  await queryClient.cancelQueries({ queryKey: deskBoardQuery.queryKey });
  replaceInBoard(queryClient, assumedAssigned(task, dept));
  useDispatchStore.getState().showToast({ taskId: task.id, roomNo: task.roomNo, dept });
  const sent = assignDeskTask(task.id, assignRequest(dept, via)).then(
    (assigned) => {
      replaceInBoard(queryClient, assigned);
      return true;
    },
    (error: unknown) => {
      const dispatch = useDispatchStore.getState();
      dispatch.dismissToast(task.id);
      const notice = assignFailureNotice(error, task.roomNo);
      if (notice?.kind === 'assignConflict') settleConflictOnBoard(queryClient, task.id, notice.context, task);
      else replaceInBoard(queryClient, task);
      if (notice) dispatch.showNotice(notice);
      return false;
    },
  );
  pendingAssigns.set(task.id, sent);
  try {
    await sent;
  } finally {
    if (pendingAssigns.get(task.id) === sent) pendingAssigns.delete(task.id);
  }
  refreshBoard(queryClient);
}

/**
 * 派單的 hook。按鈕（`via: 'button'`）、拖曳（`via: 'drag'`）、詳情面板的派單區（`via: 'button'`，
 * 它是按鈕，不是拖曳）三個入口都要走這一支，不要各自呼叫 `assignDeskTask`；
 * `via` 只是告訴後端這次是怎麼派的，寫進歷程。入口只需給「哪一張單、派去哪個部門」，
 * 不依賴待派單頁的任何東西。
 */
export function useAssignTask() {
  const queryClient = useQueryClient();
  return useCallback(
    (task: DeskTask, dept: AssignDept, via: AssignVia) => assignTaskFlow(queryClient, task, dept, via),
    [queryClient],
  );
}

/** 復原最多等派單結果這麼久（毫秒）。 */
export const UNDO_WAIT_MS = 8000;

/** 等這張單的派單有結果；沒有進行中的派單、或等逾時，都當成可以送復原。 */
async function waitForAssign(taskId: string): Promise<boolean> {
  const pending = pendingAssigns.get(taskId);
  if (!pending) return true;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<boolean>((resolve) => {
    timer = setTimeout(() => resolve(true), UNDO_WAIT_MS);
  });
  try {
    return await Promise.race([pending, timeout]);
  } finally {
    clearTimeout(timer);
  }
}

/**
 * 復原的流程本身，不依賴 React。先收掉浮條，再等派單有結果（最多 `UNDO_WAIT_MS`），再送給後端。
 * 派單請求卡住時不能永遠等：逾時就照常送，由後端回 `undo_expired` 等錯誤，交給下面的失敗處理說明。
 */
export async function undoAssignFlow(queryClient: QueryClient, toast: AssignToast) {
  const dispatch = useDispatchStore.getState();
  dispatch.dismissToast(toast.taskId);
  // 派單本身沒成功時，對話框已經說明了，不用再送復原。
  if (!(await waitForAssign(toast.taskId))) return;
  try {
    replaceInBoard(queryClient, await undoDeskAssign(toast.taskId));
  } catch (error) {
    const notice = undoFailureNotice(error, toast);
    if (notice) dispatch.showNotice(notice);
  }
  refreshBoard(queryClient);
}

/**
 * 浮條上的「復原」：先收掉浮條，再送給後端。
 * 成功時這張單回到待派單，清單依進來的時間排回原位。
 * 沒成功時這張單留在部門，被同仁認領、住客已收回、請求沒送到三種情形各跳一個對話框，
 * 因為櫃檯可能正在講電話，沒看到會以為已經收回來了。
 */
export function useUndoAssign() {
  const queryClient = useQueryClient();
  return useCallback((toast: AssignToast) => undoAssignFlow(queryClient, toast), [queryClient]);
}
