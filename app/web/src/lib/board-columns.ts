import type { DeskTask } from '../api/types';
import type { CardFilter } from '../stores/board-view';

/**
 * 總覽看板的分欄：一張單落在哪一欄、一批單裡有幾張未結案。
 * 寫成不碰畫面的純計算，欄的卡片、欄頭數量、頁副標與泳道副標才會出自同一套判斷，數字彼此對得起來。
 */

/**
 * 看板的 8 個欄。欄只是把單既有的狀態分組顯示，不是新的狀態值：
 * 待派單、待認領、已接、進行中、已完成、中斷、延後中、已結案。
 * `done` 與 `closed` 在這裡指的是欄，一欄可以收好幾種狀態的單，不要和單的狀態值混用。
 */
export type BoardColumn =
  | 'triage'
  | 'pool'
  | 'accepted'
  | 'doing'
  | 'done'
  | 'interrupted'
  | 'deferred'
  | 'closed';

/** 由左到右的 8 個欄。 */
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

/**
 * 這張單落在哪一欄。完成的單當場回答過的已經結束，放「已結案」；其餘完成的單還在等查房或驗收，放「已完成」。
 * 查房通過、免查、驗收結案、取消都放「已結案」。
 */
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

/** 點了數量卡之後看板只留下的欄；沒有篩選時 8 欄都在。 */
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

/**
 * 這一批單裡有幾張未結案。
 * 要算全館就傳整份看板的單，要算泳道的某一列就傳那一列的單；範圍由呼叫的人決定。
 */
export function countOpenTasks(tasks: readonly DeskTask[]): number {
  return tasks.filter((task) => columnOf(task) !== 'closed').length;
}

/** 時刻字串換成毫秒，好比先後。 */
function msOf(moment: string | null | undefined): number {
  return moment ? Date.parse(moment) : 0;
}

/**
 * 一欄裡的單由上到下的順序：待派單最新進來的在上；待認領等最久的在上；其他欄最後更新最新的在上。
 * 回傳新的陣列，不動傳進來的那一份。
 */
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
