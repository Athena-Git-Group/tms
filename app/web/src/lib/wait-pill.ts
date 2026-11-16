import type { DeskTask } from '../api/types';
import type { DisplayText } from './display-text';

/**
 * 卡片右上角的等待膠囊：依單的狀態與各個時刻，算出膠囊上的文字與顏色。
 * 寫成不碰畫面的純計算，是為了能用單元測試逐一驗每個分鐘數的門檻。
 *
 * 等待分鐘數與顏色分級另外各有一個函式，因為不只膠囊要用：數量卡的「等最久」、欄頭的警示、
 * 待派單頁與詳情面板的「已等 N 分」都要和膠囊出自同一個時間、同一組分級，
 * 各處自己算一份的話，同一張單在不同地方會出現不同的分鐘數或顏色。
 *
 * 這個檔目前只有型別與函式的外形，規則還沒有寫，呼叫任何一個函式都會丟出 `not implemented`。
 */

/** 膠囊的四種顏色：灰、橘、紅、綠。實際的色碼由樣式決定。 */
export type WaitPillTone = 'gray' | 'orange' | 'red' | 'green';

export interface WaitPill {
  text: DisplayText;
  tone: WaitPillTone;
}

/**
 * 這張單已經等了幾分鐘；這張單不在等的時候回 `null`。
 * `now` 要傳用後端時間校正過的現在（見 `server-clock.ts`），不要直接傳這台裝置的時間。
 */
export function waitingMinutes(_task: DeskTask, _now: Date): number | null {
  throw new Error('not implemented: lib/wait-pill.ts waitingMinutes');
}

/** 等了這麼多分鐘該用哪一種顏色。`minutes` 是 `waitingMinutes` 算出的等待分鐘數。 */
export function waitTone(_minutes: number): WaitPillTone {
  throw new Error('not implemented: lib/wait-pill.ts waitTone');
}

/**
 * 這張單的等待膠囊。
 * `now` 要傳用後端時間校正過的現在（見 `server-clock.ts`），不要直接傳這台裝置的時間。
 */
export function waitPill(_task: DeskTask, _now: Date): WaitPill {
  throw new Error('not implemented: lib/wait-pill.ts waitPill');
}
