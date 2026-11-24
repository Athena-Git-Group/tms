import { describe, expect, it } from 'vitest';
import { estimateQuota, limitNotice, quotaFromHeaders, quotaNotice, quotaRefreshMs } from './quota';

const quota = (remaining: number, resetSeconds = 0) => ({ limit: 3, remaining, resetSeconds });
const headers = (values: Record<string, string>) => new Headers(values);

describe('quotaFromHeaders', () => {
  it('讀後端給的名額標頭', () => {
    expect(
      quotaFromHeaders(headers({ 'RateLimit-Limit': '3', 'RateLimit-Remaining': '0', 'RateLimit-Reset': '75' })),
    ).toEqual({ limit: 3, remaining: 0, resetSeconds: 75 });
  });

  it('缺任一個或不是整數就當不知道', () => {
    expect(quotaFromHeaders(headers({ 'RateLimit-Limit': '3', 'RateLimit-Remaining': '2' }))).toBeNull();
    expect(quotaFromHeaders(headers({ 'RateLimit-Limit': '3', 'RateLimit-Remaining': '-1', 'RateLimit-Reset': '0' }))).toBeNull();
    expect(quotaFromHeaders(headers({}))).toBeNull();
  });
});

describe('quotaRefreshMs', () => {
  it('有名額被占用時，等最早那筆釋出再重讀一次', () => {
    expect(quotaRefreshMs(quota(1, 412))).toBe(412_000);
  });

  it('名額全空或不知道時不排重讀', () => {
    expect(quotaRefreshMs(quota(3, 0))).toBe(false);
    expect(quotaRefreshMs(null)).toBe(false);
  });
});

describe('quotaNotice', () => {
  it('估算時還能送幾項：內文帶剩餘與超出的項數', () => {
    expect(quotaNotice({ left: 1, over: 2 })).toEqual({ key: 'guest.limit.left', params: { left: 1, over: 2, sent: 0 } });
  });

  it('一項都不能送：請稍後再送出', () => {
    expect(quotaNotice({ left: 0, over: 1 }).key).toBe('guest.limit.none');
  });

  it('後端在第一張就擋下，和一項都不能送是同一句', () => {
    expect(quotaNotice({ sent: 0 }).key).toBe('guest.limit.none');
  });

  it('後端在送到一半擋下：內文帶已送出的項數', () => {
    expect(quotaNotice({ sent: 1 })).toEqual({ key: 'guest.limit.partial', params: { left: 0, over: 0, sent: 1 } });
  });
});

describe('estimateQuota', () => {
  it('額度夠時不擋', () => {
    expect(estimateQuota(1, quota(1))).toBeNull();
  });

  it('剛好用完額度也不擋', () => {
    expect(estimateQuota(3, quota(3))).toBeNull();
  });

  it('選的項目數超過剩餘額度時擋下，內文說還能送幾項', () => {
    expect(estimateQuota(3, quota(1, 60))).toEqual(limitNotice(quotaNotice({ left: 1, over: 2 })));
  });

  it('一項都不能送時擋下，內文是請稍後再送出', () => {
    expect(estimateQuota(1, quota(0, 60))?.body.key).toBe('guest.limit.none');
  });

  it('還不知道名額時不擋，交給後端判斷', () => {
    expect(estimateQuota(5, null)).toBeNull();
  });
});

describe('limitNotice', () => {
  it('暖色框，標題是 10 分鐘內最多送出 3 項', () => {
    const notice = limitNotice(quotaNotice({ sent: 1 }));
    expect([notice.tone, notice.title.key, notice.body.key]).toEqual(['caution', 'guest.limit.title', 'guest.limit.partial']);
  });
});
