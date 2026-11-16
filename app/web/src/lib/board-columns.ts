import type { DeskTask } from '../api/types';

/**
 * 總覽看板的分欄：一張單落在哪一欄、一批單裡有幾張未結案。
 * 寫成不碰畫面的純計算，欄的卡片、欄頭數量、頁副標與泳道副標才會出自同一套判斷，數字彼此對得起來。
 *
 * 這個檔目前只有型別與函式的外形，規則還沒有寫，呼叫任何一個函式都會丟出 `not implemented`。
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

/** 這張單落在哪一欄。 */
export function columnOf(_task: DeskTask): BoardColumn {
  throw new Error('not implemented: lib/board-columns.ts columnOf');
}

/**
 * 這一批單裡有幾張未結案。
 * 要算全館就傳整份看板的單，要算泳道的某一列就傳那一列的單；範圍由呼叫的人決定。
 */
export function countOpenTasks(_tasks: readonly DeskTask[]): number {
  throw new Error('not implemented: lib/board-columns.ts countOpenTasks');
}
