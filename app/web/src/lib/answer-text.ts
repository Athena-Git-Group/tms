import type { TFunction } from 'i18next';
import type { DeskTask } from '../api/types';
import { minutesSince } from './wait-pill';

/**
 * 當場回答面板的文字組法。寫成不碰畫面的純函式，畫面文字由呼叫端傳進來的 `t` 轉成當前語言。
 */

/**
 * 回覆最多幾個字，和契約 `DeskAnswerRequest.answer` 的上限、後端 `DeskAnswerText.MAX_LENGTH` 相同。
 * 「字」和後端用同一把尺：一個 Unicode code point 算一個字，中文字與表情符號都算一個。
 * 不能用字串的 `length` 或輸入框的 `maxLength`，它們數的是 UTF-16 單位，一個表情符號會算成兩個。
 */
export const ANSWER_MAX_LENGTH = 120;

/** 回覆有幾個字，算法同後端的 `codePointCount`。 */
export function answerLength(text: string): number {
  return Array.from(text).length;
}

/** 只留前 120 個字；以整個字為單位截，表情符號不會被切成半個。 */
export function clampAnswer(text: string): string {
  const chars = Array.from(text);
  return chars.length > ANSWER_MAX_LENGTH ? chars.slice(0, ANSWER_MAX_LENGTH).join('') : text;
}

/**
 * 後端 `String.strip()` 會去掉的空白，也就是 Java `Character.isWhitespace` 認的字：
 * 各種空格與換行，但不含不換行空格（U+00A0、U+2007、U+202F）。
 * JS 的 `trim()` 範圍不同（會去掉不換行空格與 U+FEFF、不去掉 U+001C–U+001F），所以不用它。
 */
const BACKEND_SPACE = String.raw`(?:(?![   ])[\p{Zs}\p{Zl}\p{Zp}\t\n\u000B\f\r\u001C-\u001F])`;
const LEADING_OR_TRAILING_SPACES = new RegExp(`^${BACKEND_SPACE}+|${BACKEND_SPACE}+$`, 'gu');

/** 去掉頭尾空白，和後端存下來、住客看到的是同一段字。 */
export function stripAnswer(text: string): string {
  return text.replace(LEADING_OR_TRAILING_SPACES, '');
}

/** 去掉頭尾空白後是 1 到 120 字才能送，和後端 `answer_invalid` 的判斷相同。 */
export function isSendableAnswer(text: string): boolean {
  const length = answerLength(stripAnswer(text));
  return length >= 1 && length <= ANSWER_MAX_LENGTH;
}

/** 住客送出到現在多久：不到 1 分寫「剛剛」，其餘寫「N 分鐘前」。還沒有校正後的現在時當成剛剛。 */
export function sentAgoText(task: DeskTask, now: Date | undefined, t: TFunction): string {
  const minutes = now ? minutesSince(task.createdAt, now) : 0;
  return minutes < 1 ? t('answer.justNow') : t('answer.minutesAgo', { minutes });
}

/**
 * 面板第一張白卡的灰字「{房號} · {住客名} · {來源} · {多久前}」。空房的單沒有住客名那一段。
 */
export function answerQuoteHead(task: DeskTask, now: Date | undefined, t: TFunction): string {
  return [task.roomNo, task.guestName, t(`board.card.sources.${task.source}`), sentAgoText(task, now, t)]
    .filter((part): part is string => Boolean(part))
    .join(' · ');
}
