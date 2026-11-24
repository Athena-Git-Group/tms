import type { GuestQuota } from '../api/types';

export function quotaFromHeaders(headers: Headers): GuestQuota | null {
  const limit = wholeNumber(headers.get('RateLimit-Limit'));
  const remaining = wholeNumber(headers.get('RateLimit-Remaining'));
  const resetSeconds = wholeNumber(headers.get('RateLimit-Reset'));
  if (limit === null || remaining === null || resetSeconds === null) return null;
  return { limit, remaining, resetSeconds };
}

function wholeNumber(value: string | null): number | null {
  if (value === null || !/^\d+$/.test(value.trim())) return null;
  return Number(value.trim());
}

type NoticeParams = { left: number; over: number; sent: number };

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

export type QuotaNotice = NoticeText<'guest.limit.left' | 'guest.limit.none' | 'guest.limit.partial'>;

export interface FormNotice {
  tone: 'caution' | 'error';
  title: NoticeText;
  body: NoticeText;
}

export function limitNotice(body: QuotaNotice): FormNotice {
  return { tone: 'caution', title: noticeText('guest.limit.title'), body };
}

export function quotaNotice(stop: { left: number; over: number } | { sent: number }): QuotaNotice {
  if ('sent' in stop) return noticeText(stop.sent > 0 ? 'guest.limit.partial' : 'guest.limit.none', stop);
  return noticeText(stop.left > 0 ? 'guest.limit.left' : 'guest.limit.none', stop);
}

export function estimateQuota(chosen: number, quota: GuestQuota | null): FormNotice | null {
  if (quota === null) return null;
  const left = quota.remaining;
  return chosen > left ? limitNotice(quotaNotice({ left, over: chosen - left })) : null;
}

export function quotaRefreshMs(quota: GuestQuota | null): number | false {
  return quota !== null && quota.remaining < quota.limit && quota.resetSeconds > 0 ? quota.resetSeconds * 1000 : false;
}
