import { describe, expect, it } from 'vitest';
import { estimateQuota, limitNotice, quotaNotice, remainingQuota } from './quota';

const sentAt = (hhmm: string) => ({ createdAt: `2026-11-20T${hhmm}:00+08:00` });
const at = (hhmm: string) => new Date(`2026-11-20T${hhmm}:00+08:00`);

describe('remainingQuota', () => {
  it('這次住宿還沒送過任何需求時剩 3', () => {
    expect(remainingQuota([], at('14:05'))).toBe(3);
  });

  it('10 分鐘內送了兩張時剩 1', () => {
    expect(remainingQuota([sentAt('14:00'), sentAt('14:02')], at('14:05'))).toBe(1);
  });

  it('已取消的也算，清單上有幾張就扣幾張，不看狀態', () => {
    expect(remainingQuota([sentAt('14:00'), sentAt('14:02'), sentAt('14:03')], at('14:08'))).toBe(0);
  });

  it('最早那筆超過 10 分鐘就不算', () => {
    expect(remainingQuota([sentAt('14:00'), sentAt('14:02'), sentAt('14:03')], at('14:11'))).toBe(1);
  });

  it('剛好 10 分鐘前送出的仍算，和後端只丟掉早於界線的一致', () => {
    expect(remainingQuota([sentAt('14:00')], at('14:10'))).toBe(2);
    expect(remainingQuota([sentAt('14:00')], new Date(at('14:10').getTime() + 1))).toBe(3);
  });

  it('超過 3 張也不會變成負數', () => {
    expect(remainingQuota(['14:00', '14:01', '14:02', '14:03'].map(sentAt), at('14:05'))).toBe(0);
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
    expect(estimateQuota(1, [sentAt('14:00'), sentAt('14:02')], at('14:05'))).toBeNull();
  });

  it('剛好用完額度也不擋', () => {
    expect(estimateQuota(3, [], at('14:05'))).toBeNull();
  });

  it('選的項目數超過剩餘額度時擋下，內文說還能送幾項', () => {
    expect(estimateQuota(3, [sentAt('14:00'), sentAt('14:02')], at('14:05'))).toEqual(limitNotice(quotaNotice({ left: 1, over: 2 })));
  });

  it('一項都不能送時擋下，內文是請稍後再送出', () => {
    expect(estimateQuota(1, [sentAt('14:00'), sentAt('14:02'), sentAt('14:03')], at('14:08'))?.body.key).toBe('guest.limit.none');
  });
});

describe('limitNotice', () => {
  it('暖色框，標題是 10 分鐘內最多送出 3 項', () => {
    const notice = limitNotice(quotaNotice({ sent: 1 }));
    expect([notice.tone, notice.title.key, notice.body.key]).toEqual(['caution', 'guest.limit.title', 'guest.limit.partial']);
  });
});
