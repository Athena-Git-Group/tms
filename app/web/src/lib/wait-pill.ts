import type { DeskTask } from '../api/types';
import { columnOf } from './board-columns';
import type { DisplayText } from './display-text';

/**
 * 卡片右上角的等待膠囊：依單的狀態與各個時刻，算出膠囊上的文字與顏色。
 * 寫成不碰畫面的純計算，是為了能用單元測試逐一驗每個分鐘數的門檻。
 *
 * 等待分鐘數與顏色分級另外各有一個函式，因為不只膠囊要用：數量卡的「等最久」、欄頭的警示、
 * 待派單頁與詳情面板的「已等 N 分」都要和膠囊出自同一個時間、同一組分級，
 * 各處自己算一份的話，同一張單在不同地方會出現不同的分鐘數或顏色。
 */

/**
 * 膠囊的四種顏色：灰、橘、紅、綠。實際的色碼由樣式決定。
 * 綠色只給已完成的單標完成時刻用，等待分鐘數的分級只會是灰、橘、紅。
 */
export type WaitPillTone = 'gray' | 'orange' | 'red' | 'green';

export interface WaitPill {
  text: DisplayText;
  tone: WaitPillTone;
}

/**
 * 這張單已經等了幾分鐘；這張單不在等的時候回 `null`。在等指的是待派單與待認領。
 * 起算時刻用後端給的 `waitingSince`（櫃檯派出的單是住客送出那一刻、被退回的單是退回那一刻），沒有時用建立時間。
 * 校正後的現在減去起算時刻，無條件捨去，不會是負數。
 * `now` 要傳用後端時間校正過的現在（見 `server-clock.ts`），不要直接傳這台裝置的時間。
 */
export function waitingMinutes(task: DeskTask, now: Date): number | null {
  if (task.state !== 'triage' && task.state !== 'pool') {
    return null;
  }
  return minutesSince(task.waitingSince ?? task.createdAt, now);
}

/**
 * 等了這麼多分鐘該用哪一種顏色：10 分起橘色、20 分起紅色，其餘灰色；不會回綠色。
 * `minutes` 是 `waitingMinutes` 算出的等待分鐘數。
 */
export function waitTone(minutes: number): WaitPillTone {
  if (minutes >= 20) {
    return 'red';
  }
  return minutes >= 10 ? 'orange' : 'gray';
}

/** 待認領的單等到這麼多分鐘算逾時；只有待認領會判斷逾時，待派單等再久都只寫「等 N 分」。 */
export const POOL_OVERDUE_MINUTES = 10;

/**
 * 從某個時刻到校正後的現在過了幾分鐘，無條件捨去，不會是負數。
 * 膠囊、詳情面板、當場回答面板寫的分鐘數都用這一個算，同一張單在各處才會是同一個數字。
 */
export function minutesSince(at: string, now: Date): number {
  return Math.max(0, Math.floor((now.getTime() - Date.parse(at)) / 60_000));
}

/**
 * 時刻寫成飯店當地的「時:分」，24 小時制、兩位數。
 * 飯店的時區就是後端伺服器的時區，`offsetMinutes` 是伺服器的時區偏移（`+08:00` 是 480，見 `server-clock.ts`）；
 * 不用這台裝置的時區，櫃檯電腦時區設錯時才不會顯示錯的時刻。
 */
export function clockTime(at: string, offsetMinutes: number): string {
  const time = new Date(Date.parse(at) + offsetMinutes * 60_000);
  return `${String(time.getUTCHours()).padStart(2, '0')}:${String(time.getUTCMinutes()).padStart(2, '0')}`;
}

/**
 * 這張單的等待膠囊，依單所在的欄：
 * - 待派單：不到 1 分「剛剛」，之後「等 N 分」。
 * - 待認領：不到 1 分「剛剛」，之後「等 N 分」；10 分起改「逾時 · N 分」，N 是超過 10 分的部分。
 * - 待派單與待認領的顏色照 `waitTone`，其餘的欄不判斷逾時。
 * - 已接：系統班前排房指派的「今日排房」，其餘「接 N 分」；進行中「做 N 分」。兩者都是灰色。
 * - 中斷「停 N 分」紅色；延後中一律「待重排」灰色。
 * - 已完成寫完成的時刻、綠色；已結案寫結束的時刻、灰色。
 * 起算時刻後端沒給時，退回這張單最後更新的時刻。
 * `now` 要傳用後端時間校正過的現在（見 `server-clock.ts`），不要直接傳這台裝置的時間。
 * `serverUtcOffsetMinutes` 是後端伺服器的時區偏移（分鐘），已完成與已結案的時刻照它顯示。
 */
export function waitPill(task: DeskTask, now: Date, serverUtcOffsetMinutes: number): WaitPill {
  const column = columnOf(task);
  switch (column) {
    case 'triage':
    case 'pool': {
      const minutes = waitingMinutes(task, now) ?? 0;
      const tone = waitTone(minutes);
      if (minutes < 1) {
        return { text: { key: 'board.pill.justNow' }, tone };
      }
      if (column === 'pool' && minutes >= POOL_OVERDUE_MINUTES) {
        return { text: { key: 'board.pill.overdue', values: { minutes: minutes - POOL_OVERDUE_MINUTES } }, tone };
      }
      return { text: { key: 'board.pill.waiting', values: { minutes } }, tone };
    }
    case 'accepted':
      if (task.acceptedVia === 'plan_assigned') {
        return { text: { key: 'board.pill.planned' }, tone: 'gray' };
      }
      return {
        text: { key: 'board.pill.accepted', values: { minutes: minutesSince(task.acceptedAt ?? task.updatedAt, now) } },
        tone: 'gray',
      };
    case 'doing':
      return {
        text: { key: 'board.pill.doing', values: { minutes: minutesSince(task.startedAt ?? task.updatedAt, now) } },
        tone: 'gray',
      };
    case 'interrupted':
      return {
        text: {
          key: 'board.pill.stopped',
          values: { minutes: minutesSince(task.interruptedAt ?? task.updatedAt, now) },
        },
        tone: 'red',
      };
    case 'deferred':
      return { text: { key: 'board.pill.deferred' }, tone: 'gray' };
    case 'done':
      return {
        text: { key: 'board.pill.at', values: { time: clockTime(task.doneAt ?? task.updatedAt, serverUtcOffsetMinutes) } },
        tone: 'green',
      };
    case 'closed':
      return {
        text: { key: 'board.pill.at', values: { time: clockTime(task.endedAt ?? task.updatedAt, serverUtcOffsetMinutes) } },
        tone: 'gray',
      };
  }
}
