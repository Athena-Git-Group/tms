import type { GuestTask } from '../api/types';

export const QUOTA_PER_TEN_MINUTES = 3;
const WINDOW_MS = 10 * 60 * 1000;

/**
 * 3 減去送出時間在 `now` 往前 10 分鐘內的張數，最少是 0；已取消的也算，因為後端的限流也算。
 * 剛好 10 分鐘前送出的仍算在內，和後端只丟掉早於界線的那幾筆一致。
 */
export function remainingQuota(tasks: readonly Pick<GuestTask, 'createdAt'>[], now: Date): number {
  const cutoff = now.getTime() - WINDOW_MS;
  const recent = tasks.filter((task) => Date.parse(task.createdAt) >= cutoff).length;
  return Math.max(QUOTA_PER_TEN_MINUTES - recent, 0);
}

type NoticeParams = { left: number; over: number; sent: number };

/** 提示框的一行字：語系鍵與它要的參數；參數一律帶齊三個，用不到的是 0。 */
export interface NoticeText<K extends string = NoticeKey> {
  key: K;
  params: NoticeParams;
}

export type NoticeKey =
  | 'guest.limit.title'
  | 'guest.limit.left'
  | 'guest.limit.none'
  | 'guest.limit.partial'
  | 'guest.notSent.title'
  | 'guest.notSent.partialTitle'
  | 'guest.notSent.body';

export function noticeText<K extends NoticeKey>(key: K, params: Partial<NoticeParams> = {}): NoticeText<K> {
  return { key, params: { left: 0, over: 0, sent: 0, ...params } };
}

/** 額度提示框的內文。標題一律是「10 分鐘內最多送出 3 項」。 */
export type QuotaNotice = NoticeText<'guest.limit.left' | 'guest.limit.none' | 'guest.limit.partial'>;

/** 表單下方不會自己消失的提示框：暖色是額度，紅色是沒送成。 */
export interface FormNotice {
  tone: 'caution' | 'error';
  title: NoticeText;
  body: NoticeText;
}

export function limitNotice(body: QuotaNotice): FormNotice {
  return { tone: 'caution', title: noticeText('guest.limit.title'), body };
}

/**
 * 依擋下的原因選內文：估算時還能送幾項、超出幾項；後端擋下時已經送出幾項。
 * 一項都不能送、或後端在第一張就擋下，是同一句「請稍後再送出」。
 */
export function quotaNotice(stop: { left: number; over: number } | { sent: number }): QuotaNotice {
  if ('sent' in stop) return noticeText(stop.sent > 0 ? 'guest.limit.partial' : 'guest.limit.none', stop);
  return noticeText(stop.left > 0 ? 'guest.limit.left' : 'guest.limit.none', stop);
}

/** 送出前的估算：選的項目數超過剩餘額度時一張都不送，回要顯示的提示框；額度夠時是 `null`。 */
export function estimateQuota(chosen: number, tasks: readonly Pick<GuestTask, 'createdAt'>[], now: Date): FormNotice | null {
  const left = remainingQuota(tasks, now);
  return chosen > left ? limitNotice(quotaNotice({ left, over: chosen - left })) : null;
}
