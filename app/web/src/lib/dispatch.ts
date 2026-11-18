import { ApiError, NetworkError } from '../api/client';
import type { ConflictContext } from './conflict-text';
import type { DeskAssignRequest, DeskBoard, DeskTask } from '../api/types';

/**
 * 派單與復原的純計算：看板快取怎麼跟著變、復原失敗要說什麼。
 * 寫成不碰畫面的純函式，方便用單元測試驗。
 */

/** 能派去的部門。 */
export type AssignDept = 'housekeeping' | 'engineering';

/** 這次派單是怎麼派的：按鈕（含詳情面板的派單區）或拖曳。寫進歷程。 */
export type AssignVia = 'button' | 'drag';

/** 派單請求的內容；三個入口都用它組，`via` 原樣帶給後端。 */
export function assignRequest(dept: AssignDept, via: AssignVia): DeskAssignRequest {
  return { dept, via };
}

/**
 * 看板資料裡把同一張單換成新的樣子；看板裡沒有這張單時原樣回傳。
 * 看板上那張的步數（`seq`）比較大時也原樣回傳：派單、復原、當場回答的回應可能比即時訊號帶來的卡片晚到，
 * 換上它會把畫面倒回舊的樣子，而之後不一定還有訊號來糾正。步數一樣時照換，按下去先畫的樣子才能蓋上去。
 */
export function withTaskReplaced(board: DeskBoard, task: DeskTask): DeskBoard {
  const current = board.tasks.find((item) => item.id === task.id);
  if (!current || current.seq > task.seq) return board;
  return { ...board, tasks: board.tasks.map((item) => (item.id === task.id ? task : item)) };
}

/**
 * 按下派單、後端還沒回之前先畫的樣子：這張單進那個部門的待認領。
 * 後端回來之後換成它回的那一份。
 */
export function assumedAssigned(task: DeskTask, dept: AssignDept): DeskTask {
  return { ...task, state: 'pool', dept };
}

/**
 * 撞單之後、看板重讀回來之前先畫的樣子：照後端說的「這張單怎麼離開待派單」把它移出待派單。
 * 對話框說「看板已更新成最新狀態」，畫面不能還留著這張單等重讀。
 * 後端沒說是哪一種、或派走卻沒帶合法的部門時不知道該放哪一欄，看板原樣回傳，等重讀。
 */
export function withConflictSettled(board: DeskBoard, taskId: string, context: ConflictContext): DeskBoard {
  const task = board.tasks.find((item) => item.id === taskId);
  if (!task) return board;
  switch (context.resolution) {
    case 'assigned':
      return isAssignDept(context.dept) ? withTaskReplaced(board, assumedAssigned(task, context.dept)) : board;
    case 'answered':
      return withTaskReplaced(board, { ...task, state: 'done', answered: true, endedAt: task.endedAt ?? task.updatedAt });
    case 'guest_withdrew':
      return withTaskReplaced(board, { ...task, state: 'cancelled' });
    default:
      return board;
  }
}

/** 撞單對話框要的內容：這張單的房號與後端回的 `context`。派單與當場回答共用。 */
export interface ConflictNotice {
  roomNo: string;
  context: ConflictContext;
}

/** 派單、復原沒成功時要跳的對話框，帶著組句子要的內容。部門是代碼，由畫面換成那個語言的名稱。 */
export type DispatchNotice =
  | { kind: 'undoClaimed'; name: string; roomNo: string; dept: AssignDept }
  | { kind: 'undoGuestWithdrew'; roomNo: string }
  | { kind: 'undoNotSent'; roomNo: string; dept: AssignDept }
  | ({ kind: 'assignConflict' } & ConflictNotice)
  | { kind: 'assignNotSent'; roomNo: string };

/** 後端說這張單已經不是待派單時的錯誤碼，`context.resolution` 說明它怎麼離開待派單。 */
export const TASK_NOT_IN_TRIAGE = 'task_not_in_triage';

/** 派單或當場回答沒成功是不是因為別人先處理掉了（撞單）；撞單時畫面要更新成最新狀態，而不是還原成待派單。 */
export function isAssignConflict(error: unknown): error is ApiError {
  return error instanceof ApiError && error.code === TASK_NOT_IN_TRIAGE;
}

/**
 * 派單沒成功時跳哪一個對話框。
 * 撞單帶著後端回的 `context`，由 `conflictText` 依「這張單怎麼離開待派單」組字；
 * 其餘的失敗（例如斷線）一律告知沒有派成功、單還在待派單。
 * 登入過期由統一的處理帶回登入頁，這裡不跳對話框，回 `null`。
 */
export function assignFailureNotice(error: unknown, roomNo: string): DispatchNotice | null {
  if (error instanceof ApiError && error.status === 401) return null;
  if (!isAssignConflict(error)) return { kind: 'assignNotSent', roomNo };
  return { kind: 'assignConflict', roomNo, context: error.context };
}

/** 當場回答沒送成時要跳的對話框：撞單（別人先處理掉了）或沒有送出。 */
export type AnswerNotice = ({ kind: 'answerConflict' } & ConflictNotice) | { kind: 'answerNotSent'; roomNo: string };

/**
 * 「回覆並結案」沒成功時跳哪一個對話框，分法和派單相同：
 * 撞單帶著後端回的 `context`，交給 `conflictText` 組字；其餘的失敗一律告知回覆還沒送出；
 * 登入過期交給統一的處理，回 `null`。
 */
export function answerFailureNotice(error: unknown, roomNo: string): AnswerNotice | null {
  if (error instanceof ApiError && error.status === 401) return null;
  if (!isAssignConflict(error)) return { kind: 'answerNotSent', roomNo };
  return { kind: 'answerConflict', roomNo, context: error.context };
}

/** 浮條上正在等復原的那一次派單。 */
export interface UndoTarget {
  roomNo: string;
  dept: AssignDept;
}

/**
 * 復原沒成功時跳哪一個對話框。
 * 已被同仁認領、住客已收回、請求沒送到後端三種各有一句；其他原因不跳對話框，回 `null`。
 */
export function undoFailureNotice(error: unknown, target: UndoTarget): DispatchNotice | null {
  if (error instanceof NetworkError) {
    return { kind: 'undoNotSent', roomNo: target.roomNo, dept: target.dept };
  }
  if (!(error instanceof ApiError)) return null;
  const roomNo = stringOr(error.context.roomNo, target.roomNo);
  switch (error.code) {
    case 'undo_claimed':
      return {
        kind: 'undoClaimed',
        name: stringOr(error.context.assigneeName, ''),
        roomNo,
        dept: isAssignDept(error.context.dept) ? error.context.dept : target.dept,
      };
    case 'undo_guest_withdrew':
      return { kind: 'undoGuestWithdrew', roomNo };
    default:
      return null;
  }
}

/** 是不是派得過去的部門（房務或工務）；後端回應、畫面上的屬性讀回來的值都先過這一關。 */
export function isAssignDept(value: unknown): value is AssignDept {
  return value === 'housekeeping' || value === 'engineering';
}

function stringOr(value: unknown, fallback: string): string {
  return typeof value === 'string' && value !== '' ? value : fallback;
}
