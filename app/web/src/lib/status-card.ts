import type { DeskTask, DeskTaskDetail } from '../api/types';
import type { DisplayText } from './display-text';

/**
 * 單筆詳情面板的狀態標籤與狀態訊息卡：依單的內容算出要顯示的文字與顏色。
 * 寫成不碰畫面的純計算，是為了能用單元測試逐一驗每一種標籤與訊息卡。
 *
 * 分成兩個函式，是因為兩者能算的時間點不同：標籤只需要看板清單就有的資料，面板一打開就能顯示；
 * 訊息卡要等單筆詳情讀回來才有足夠的內容。
 *
 * 這個檔目前只有型別與函式的外形，規則還沒有寫，呼叫任何一個函式都會丟出 `not implemented`。
 */

/** 狀態標籤的六種顏色：黃、品牌淺底、灰、品牌色實底、淡綠、淡紅。實際的色碼由樣式決定。 */
export type StatusTagTone = 'yellow' | 'brand-soft' | 'gray' | 'brand' | 'green' | 'red';

export interface StatusTag {
  text: DisplayText;
  tone: StatusTagTone;
}

/** 狀態訊息卡的四種底色：淡綠、品牌淺底、淡紅、白。 */
export type StatusCardTone = 'green' | 'brand-soft' | 'red' | 'white';

export interface StatusCard {
  title: DisplayText;
  /** 有些狀況只有標題一句，沒有說明。 */
  description: DisplayText | null;
  tone: StatusCardTone;
}

/** 這張單的狀態標籤。 */
export function statusTag(_task: DeskTask): StatusTag {
  throw new Error('not implemented: lib/status-card.ts statusTag');
}

/**
 * 這張單的狀態訊息卡；不顯示訊息卡的單回 `null`。
 * `now` 要傳用後端時間校正過的現在（見 `server-clock.ts`）。
 */
export function statusCard(_task: DeskTaskDetail, _now: Date): StatusCard | null {
  throw new Error('not implemented: lib/status-card.ts statusCard');
}
