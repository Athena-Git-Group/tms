import type { TFunction } from 'i18next';
import type { DeskTask } from '../api/types';
import { minutesSince } from './wait-pill';

/**
 * 回答最多 120 個字，長度一律用 Unicode 碼位數計算，和後端 `DeskAnswerText` 用的 `codePointCount` 一致，
 * 前端放行的回答後端才不會因為長度拒收。字串的 `length` 與輸入框的 `maxLength` 數的是 UTF-16 單位，
 * 一個表情符號會算成兩個。碼位數也不完全等於使用者看到的字數，由多個碼位組成的表情符號會算成好幾個字，
 * 但兩端數法一致比較重要，所以不在單邊改用其他數法。
 */
export const ANSWER_MAX_LENGTH = 120;

export function answerLength(text: string): number {
  return Array.from(text).length;
}

export function clampAnswer(text: string): string {
  const chars = Array.from(text);
  return chars.length > ANSWER_MAX_LENGTH ? chars.slice(0, ANSWER_MAX_LENGTH).join('') : text;
}

/**
 * 去除前後空白的規則要和後端 `String.strip()` 一致，也就是 Java `Character.isWhitespace` 認定的字元：
 * 各種空格與換行，但不含不換行空格 U+00A0、U+2007、U+202F。JS 的 `trim()` 會去掉不換行空格與 U+FEFF，
 * 卻不去掉 U+001C 到 U+001F，範圍不同，所以不用它。
 */
const BACKEND_SPACE = String.raw`(?:(?![   ])[\p{Zs}\p{Zl}\p{Zp}\t\n\u000B\f\r\u001C-\u001F])`;
const LEADING_OR_TRAILING_SPACES = new RegExp(`^${BACKEND_SPACE}+|${BACKEND_SPACE}+$`, 'gu');

export function stripAnswer(text: string): string {
  return text.replace(LEADING_OR_TRAILING_SPACES, '');
}

export function isSendableAnswer(text: string): boolean {
  const length = answerLength(stripAnswer(text));
  return length >= 1 && length <= ANSWER_MAX_LENGTH;
}

export function sentAgoText(task: DeskTask, now: Date | undefined, t: TFunction): string {
  const minutes = now ? minutesSince(task.createdAt, now) : 0;
  return minutes < 1 ? t('answer.justNow') : t('answer.minutesAgo', { minutes });
}

export function answerQuoteHead(task: DeskTask, now: Date | undefined, t: TFunction): string {
  return [task.roomNo, task.guestName, t(`board.card.sources.${task.source}`), sentAgoText(task, now, t)]
    .filter((part): part is string => Boolean(part))
    .join(' · ');
}
