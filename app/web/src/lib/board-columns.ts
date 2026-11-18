import type { DeskTask } from '../api/types';
import type { CardFilter } from '../stores/board-view';

export type BoardColumn =
  | 'triage'
  | 'pool'
  | 'accepted'
  | 'doing'
  | 'done'
  | 'interrupted'
  | 'deferred'
  | 'closed';

export const BOARD_COLUMNS: readonly BoardColumn[] = [
  'triage',
  'pool',
  'accepted',
  'doing',
  'done',
  'interrupted',
  'deferred',
  'closed',
];

export function columnOf(task: DeskTask): BoardColumn {
  switch (task.state) {
    case 'triage':
    case 'pool':
    case 'accepted':
    case 'doing':
    case 'interrupted':
    case 'deferred':
      return task.state;
    case 'done':
      return task.answered ? 'closed' : 'done';
    case 'inspected':
    case 'closed':
    case 'cancelled':
      return 'closed';
  }
}

export function columnsShownFor(filter: CardFilter | null): readonly BoardColumn[] {
  switch (filter) {
    case 'triage':
      return ['triage'];
    case 'pool':
      return ['pool'];
    case 'in_progress':
      return ['accepted', 'doing'];
    case null:
      return BOARD_COLUMNS;
  }
}

export function countOpenTasks(tasks: readonly DeskTask[]): number {
  return tasks.filter((task) => columnOf(task) !== 'closed').length;
}

function msOf(moment: string | null | undefined): number {
  return moment ? Date.parse(moment) : 0;
}

export function sortInColumn(column: BoardColumn, tasks: readonly DeskTask[]): DeskTask[] {
  const sorted = [...tasks];
  switch (column) {
    case 'triage':
      return sorted.sort((a, b) => msOf(b.createdAt) - msOf(a.createdAt));
    case 'pool':
      return sorted.sort((a, b) => msOf(a.waitingSince ?? a.createdAt) - msOf(b.waitingSince ?? b.createdAt));
    default:
      return sorted.sort((a, b) => msOf(b.updatedAt) - msOf(a.updatedAt));
  }
}
