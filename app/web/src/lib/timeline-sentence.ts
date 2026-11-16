import type { DeskTaskDetail, DeskTimelineEntry } from '../api/types';
import type { DisplayText } from './display-text';

/**
 * 單筆詳情面板的歷程：把後端回的一筆事件換成畫面上的一句話與左邊的色點。
 * 句子由畫面依事件的種類與內容組出來，四種介面語言才換得動；
 * 寫成不碰畫面的純計算，是為了能用單元測試逐一驗每一種事件的句子。
 *
 * 這個檔目前只有型別與函式的外形，句子還沒有寫，呼叫函式會丟出 `not implemented`。
 */

/** 歷程色點的五種顏色：品牌色、灰、橘、紫、綠。實際的色碼由樣式決定。 */
export type TimelineDotTone = 'brand' | 'gray' | 'orange' | 'purple' | 'green';

export interface TimelineSentence {
  text: DisplayText;
  dot: TimelineDotTone;
}

/**
 * 這一筆事件在歷程上的句子與色點；歷程上不顯示的事件回 `null`，畫面略過那一筆。
 * `task` 是這筆事件所屬的單，有些句子要用到單的標題或類型。
 */
export function timelineSentence(
  _entry: DeskTimelineEntry,
  _task: DeskTaskDetail,
): TimelineSentence | null {
  throw new Error('not implemented: lib/timeline-sentence.ts timelineSentence');
}
