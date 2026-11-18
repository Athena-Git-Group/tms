import type { DeskTask } from '../api/types';
import { columnOf, sortInColumn } from './board-columns';

/**
 * 待派單頁的清單與頁首計數怎麼從看板資料挑出來。
 * 寫成不碰畫面的純函式，方便用單元測試驗。
 */

/** 全館的待派單，最新進來的在最上面。和總覽看板「待派單」欄是同一批、同一個順序。 */
export function triageTasks(tasks: readonly DeskTask[]): DeskTask[] {
  return sortInColumn(
    'triage',
    tasks.filter((task) => columnOf(task) === 'triage'),
  );
}

/** 某個部門的待認領有幾張，頁首的「房務 N 單待接」「工務 N 單待接」用。 */
export function poolCount(tasks: readonly DeskTask[], dept: 'housekeeping' | 'engineering'): number {
  return tasks.filter((task) => columnOf(task) === 'pool' && task.dept === dept).length;
}
