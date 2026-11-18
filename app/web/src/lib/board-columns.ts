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
 * 這張單落在哪一欄。後端記了結束時刻（`endedAt` 有值）的單一律放「已結案」，
 * 包括查房通過、免查、驗收結案、取消、當場回答，以及完成當下就結束的備品與其他類單。
 * 「已完成」只放完成了、還在等查房或驗收的單；只看 `state` 的話，完成的備品單會一直佔著「已完成」，頁副標也會把它算成未結案。
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
      return task.endedAt ? 'closed' : 'done';
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
 * 後端給看板時的順序：建立時間新的在上，同一刻建立的再比 id，大的在上。
 * 拿它當每一欄最後的比較，時刻相同的單才不會因為即時更新把新卡片併進陣列的位置不同，跟重新整理後排得不一樣。
 */
function byServerOrder(a: DeskTask, b: DeskTask): number {
  return msOf(b.createdAt) - msOf(a.createdAt) || (a.id < b.id ? 1 : a.id > b.id ? -1 : 0);
}

/**
 * 一欄裡的單由上到下的順序：待派單最新進來的在上；待認領等最久的在上；其他欄最後更新最新的在上。
 * 時刻相同時照後端給看板的順序。回傳新的陣列，不動傳進來的那一份。
 */
export function sortInColumn(column: BoardColumn, tasks: readonly DeskTask[]): DeskTask[] {
  const sorted = [...tasks];
  switch (column) {
    case 'triage':
      return sorted.sort(byServerOrder);
    case 'pool':
      return sorted.sort(
        (a, b) => msOf(a.waitingSince ?? a.createdAt) - msOf(b.waitingSince ?? b.createdAt) || byServerOrder(a, b),
      );
    default:
      return sorted.sort((a, b) => msOf(b.updatedAt) - msOf(a.updatedAt) || byServerOrder(a, b));
  }
}
