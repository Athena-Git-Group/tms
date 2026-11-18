import { ApiError, NetworkError } from '../api/client';
import type { ConflictContext } from './conflict-text';
import type { DeskAssignRequest, DeskBoard, DeskTask } from '../api/types';

export type AssignDept = 'housekeeping' | 'engineering';

export type AssignVia = 'button' | 'drag';

export function assignRequest(dept: AssignDept, via: AssignVia): DeskAssignRequest {
  return { dept, via };
}

export function withTaskReplaced(board: DeskBoard, task: DeskTask): DeskBoard {
  const current = board.tasks.find((item) => item.id === task.id);
  if (!current || current.seq > task.seq) return board;
  return { ...board, tasks: board.tasks.map((item) => (item.id === task.id ? task : item)) };
}

export function assumedAssigned(task: DeskTask, dept: AssignDept): DeskTask {
  return { ...task, state: 'pool', dept };
}

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

export interface ConflictNotice {
  roomNo: string;
  context: ConflictContext;
}

export type DispatchNotice =
  | { kind: 'undoClaimed'; name: string; roomNo: string; dept: AssignDept }
  | { kind: 'undoGuestWithdrew'; roomNo: string }
  | { kind: 'undoNotSent'; roomNo: string; dept: AssignDept }
  | ({ kind: 'assignConflict' } & ConflictNotice)
  | { kind: 'assignNotSent'; roomNo: string };

export const TASK_NOT_IN_TRIAGE = 'task_not_in_triage';

export function isAssignConflict(error: unknown): error is ApiError {
  return error instanceof ApiError && error.code === TASK_NOT_IN_TRIAGE;
}

export function assignFailureNotice(error: unknown, roomNo: string): DispatchNotice | null {
  if (error instanceof ApiError && error.status === 401) return null;
  if (!isAssignConflict(error)) return { kind: 'assignNotSent', roomNo };
  return { kind: 'assignConflict', roomNo, context: error.context };
}

export type AnswerNotice = ({ kind: 'answerConflict' } & ConflictNotice) | { kind: 'answerNotSent'; roomNo: string };

export function answerFailureNotice(error: unknown, roomNo: string): AnswerNotice | null {
  if (error instanceof ApiError && error.status === 401) return null;
  if (!isAssignConflict(error)) return { kind: 'answerNotSent', roomNo };
  return { kind: 'answerConflict', roomNo, context: error.context };
}

export interface UndoTarget {
  roomNo: string;
  dept: AssignDept;
}

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

export function isAssignDept(value: unknown): value is AssignDept {
  return value === 'housekeeping' || value === 'engineering';
}

function stringOr(value: unknown, fallback: string): string {
  return typeof value === 'string' && value !== '' ? value : fallback;
}
