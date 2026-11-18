import type { DeskTask } from '../api/types';
import { columnOf, sortInColumn, type BoardColumn } from './board-columns';
import { waitingMinutes, waitTone } from './wait-pill';

/**
 * 總覽看板上的各種計數：四張數量卡的數字、「等最久」是哪張單、各欄的張數與欄頭警示。
 * 寫成不碰畫面的純計算，數量卡、欄頭與之後的待派單徽章才會出自同一份看板資料、同一個等待分鐘數。
 *
 * 範圍有兩種，由呼叫的人分別傳進來：數量卡永遠算全館（`hotelTasks`，看板回應的整份單），
 * 欄頭只算套用房號搜尋之後還留在看板上的卡片（`searchedTasks`）。搜尋房號會改變欄頭的數字，不會改變數量卡。
 */

/** 數量卡數字的顏色：一般、橘色、紅色。數量卡沒有綠色。 */
export type TileTone = 'gray' | 'orange' | 'red';

/** 「等最久」那一張單、它等了幾分鐘、數字該用的顏色。 */
export interface LongestWaiting {
  task: DeskTask;
  /** 還沒有校正後的現在時是 `undefined`，畫面先不顯示數字。 */
  minutes: number | undefined;
  tone: TileTone;
}

/** 四張數量卡要的數字，全館範圍。待派單徽章也用 `triage` 這個數字。 */
export interface TileCounts {
  triage: number;
  pool: number;
  /** 待認領裡房務、工務各幾張，數量卡的說明要寫出來。 */
  poolByDept: { housekeeping: number; engineering: number };
  /** 「處理中」數量卡的數字，已接與進行中兩欄都算。 */
  inProgress: number;
  /** 待派單與待認領裡等最久的那張；沒有在等的單時是 `null`。 */
  longest: LongestWaiting | null;
}

/**
 * 欄頭的警示：`overdue` 是待派單或待認領裡等了 20 分以上的張數，
 * `awaitingInspection` 是已完成欄裡等主管查房的打掃單張數。
 */
export interface ColumnWarning {
  kind: 'overdue' | 'awaitingInspection';
  count: number;
}

/** 一欄要畫的東西：欄內由上到下的單、欄頭張數與警示（沒有該提醒的單時是 `null`）。 */
export interface ColumnHead {
  column: BoardColumn;
  tasks: DeskTask[];
  count: number;
  warning: ColumnWarning | null;
}

/** 欄頭「逾 20 分」的門檻：等了這麼多分鐘以上的單要提醒。 */
export const OVERDUE_MINUTES = 20;

function inColumns(tasks: readonly DeskTask[], ...columns: BoardColumn[]): DeskTask[] {
  return tasks.filter((task) => columns.includes(columnOf(task)));
}

/**
 * 等最久的那張單：待派單與待認領裡起算時刻最早的，起算時刻相同時取先出現的那張。
 * 數字的顏色照等待分鐘數的分級；分級的型別裡有綠色，但等待分鐘數不會分到綠色，萬一出現也當一般顯示。
 */
function longestWaiting(hotelTasks: readonly DeskTask[], now: Date | undefined): LongestWaiting | null {
  const task = sortInColumn('pool', inColumns(hotelTasks, 'triage', 'pool'))[0];
  if (!task) {
    return null;
  }
  const minutes = now ? (waitingMinutes(task, now) ?? undefined) : undefined;
  const tone = waitTone(minutes ?? 0);
  return { task, minutes, tone: tone === 'green' ? 'gray' : tone };
}

/** 四張數量卡的數字。`hotelTasks` 要傳全館的單，不要傳搜尋過的。 */
export function tileCounts(hotelTasks: readonly DeskTask[], now: Date | undefined): TileCounts {
  const pool = inColumns(hotelTasks, 'pool');
  return {
    triage: inColumns(hotelTasks, 'triage').length,
    pool: pool.length,
    poolByDept: {
      housekeeping: pool.filter((task) => task.dept === 'housekeeping').length,
      engineering: pool.filter((task) => task.dept === 'engineering').length,
    },
    inProgress: inColumns(hotelTasks, 'accepted', 'doing').length,
    longest: longestWaiting(hotelTasks, now),
  };
}

function columnWarning(column: BoardColumn, tasks: readonly DeskTask[], now: Date | undefined): ColumnWarning | null {
  let kind: ColumnWarning['kind'];
  let count: number;
  if ((column === 'triage' || column === 'pool') && now) {
    kind = 'overdue';
    count = tasks.filter((task) => (waitingMinutes(task, now) ?? 0) >= OVERDUE_MINUTES).length;
  } else if (column === 'done') {
    kind = 'awaitingInspection';
    count = tasks.filter((task) => task.type === 'cleaning').length;
  } else {
    return null;
  }
  return count > 0 ? { kind, count } : null;
}

/**
 * 看板上要畫的各欄，順序照 `columns`。`searchedTasks` 要傳套用房號搜尋之後的單，欄頭數字才跟著搜尋變。
 * 還沒有校正後的現在時，待派單與待認領不顯示「逾 20 分」。
 */
export function columnHeads(
  searchedTasks: readonly DeskTask[],
  columns: readonly BoardColumn[],
  now: Date | undefined,
): ColumnHead[] {
  return columns.map((column) => {
    const tasks = sortInColumn(column, inColumns(searchedTasks, column));
    return { column, tasks, count: tasks.length, warning: columnWarning(column, tasks, now) };
  });
}
