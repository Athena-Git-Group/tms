import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { assignDeskTask, cancelBoardReadForDispatch, deskBoardQuery, undoDeskAssign } from '../../api/desk';
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

function refreshBoard(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: deskBoardQuery.queryKey });
}

const pendingAssigns = new Map<string, Promise<boolean>>();

export async function assignTaskFlow(queryClient: QueryClient, task: DeskTask, dept: AssignDept, via: AssignVia) {
  await cancelBoardReadForDispatch(queryClient);
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

export function useAssignTask() {
  const queryClient = useQueryClient();
  return useCallback(
    (task: DeskTask, dept: AssignDept, via: AssignVia) => assignTaskFlow(queryClient, task, dept, via),
    [queryClient],
  );
}

export const UNDO_WAIT_MS = 8000;

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

export async function undoAssignFlow(queryClient: QueryClient, toast: AssignToast) {
  const dispatch = useDispatchStore.getState();
  dispatch.dismissToast(toast.taskId);
  if (!(await waitForAssign(toast.taskId))) return;
  try {
    replaceInBoard(queryClient, await undoDeskAssign(toast.taskId));
  } catch (error) {
    const notice = undoFailureNotice(error, toast);
    if (notice) dispatch.showNotice(notice);
  }
  refreshBoard(queryClient);
}

export function useUndoAssign() {
  const queryClient = useQueryClient();
  return useCallback((toast: AssignToast) => undoAssignFlow(queryClient, toast), [queryClient]);
}
