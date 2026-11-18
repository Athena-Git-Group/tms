import { describe, expect, it } from 'vitest';
import { clockOffsetMs, correctedNow, dateAtUtcOffset, utcOffsetMinutes } from './server-clock';

describe('clockOffsetMs', () => {
  it('後端比裝置快時是正數', () => {
    expect(clockOffsetMs('2026-10-26T06:10:00Z', new Date('2026-10-26T06:08:30Z'))).toBe(90_000);
  });

  it('後端比裝置慢時是負數', () => {
    expect(clockOffsetMs('2026-10-26T06:10:00Z', new Date('2026-10-26T06:10:05Z'))).toBe(-5_000);
  });

  it('帶時區的時刻字串照時區換算', () => {
    expect(clockOffsetMs('2026-10-26T14:10:00+08:00', new Date('2026-10-26T06:10:00Z'))).toBe(0);
  });
});

describe('correctedNow', () => {
  it('裝置的時間加上差值', () => {
    expect(correctedNow(90_000, new Date('2026-10-26T06:08:30Z')).toISOString()).toBe('2026-10-26T06:10:00.000Z');
  });

  it('收到回應之後過了多久，校正後的現在就往後走多久', () => {
    const offset = clockOffsetMs('2026-10-26T06:10:00Z', new Date('2026-10-26T06:00:00Z'));
    expect(correctedNow(offset, new Date('2026-10-26T06:03:00Z')).toISOString()).toBe('2026-10-26T06:13:00.000Z');
  });
});

describe('utcOffsetMinutes', () => {
  it.each([
    ['2026-10-26T14:10:00+08:00', 480],
    ['2026-10-26T00:40:00.123456-05:30', -330],
    ['2026-10-26T06:10:00Z', 0],
    ['2026-10-26T06:10:00z', 0],
    ['2026-10-26T14:10:00+0800', 480],
  ])('%s 是 %i 分', (serverTime, minutes) => {
    expect(utcOffsetMinutes(serverTime)).toBe(minutes);
  });

  it('尾端沒有偏移時回 null', () => {
    expect(utcOffsetMinutes('2026-10-26T14:10:00')).toBeNull();
  });
});

describe('dateAtUtcOffset', () => {
  it('照給的時區偏移算是哪一天', () => {
    expect(dateAtUtcOffset(new Date('2026-10-26T06:10:00Z'), 480)).toBe('2026-10-26');
  });

  it('跨過午夜時換成前一天或後一天', () => {
    expect(dateAtUtcOffset(new Date('2026-10-25T17:00:00Z'), 480)).toBe('2026-10-26');
    expect(dateAtUtcOffset(new Date('2026-10-26T03:00:00Z'), -330)).toBe('2026-10-25');
  });
});
