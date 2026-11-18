import type { TFunction } from 'i18next';
import { describe, expect, it } from 'vitest';
import type { DeskTaskDetail } from '../api/types';
import { createdAgoText, detailContentRows } from './detail-content';

const t = ((key: string, options?: Record<string, string | number>) =>
  options ? `${key}|${Object.values(options).join('|')}` : key) as unknown as TFunction;

const createdAt = new Date('2026-10-26T06:00:00Z');

const TAIPEI = 480;

function task(overrides: Partial<DeskTaskDetail> = {}): DeskTaskDetail {
  return {
    id: '1',
    displayNo: 'T-1',
    state: 'accepted',
    answered: false,
    dept: 'housekeeping',
    type: 'cleaning',
    source: 'guest',
    roomNo: '305',
    floor: 3,
    vip: false,
    guestName: '陳怡安',
    title: '加毛巾',
    guestTyped: false,
    assigneeName: '林美玲',
    createdAt: createdAt.toISOString(),
    updatedAt: createdAt.toISOString(),
    serverTime: createdAt.toISOString(),
    guest: { name: '陳怡安', checkinDate: '2026-10-25', checkoutDate: '2026-10-27' },
    timeline: [],
    ...overrides,
  };
}

function minutesAfter(minutes: number): Date {
  return new Date(createdAt.getTime() + minutes * 60_000);
}

function valueOf(row: number, overrides: Partial<DeskTaskDetail> = {}, now: Date | undefined = minutesAfter(5)) {
  return detailContentRows(task(overrides), now, TAIPEI, t)[row]?.value;
}

describe('detailContentRows', () => {
  it('由上到下是房號、住客、部門、負責人、來源、建立六列', () => {
    expect(detailContentRows(task(), minutesAfter(5), TAIPEI, t).map((row) => row.label)).toEqual([
      'detail.content.room',
      'detail.content.guest',
      'detail.content.dept',
      'detail.content.assignee',
      'detail.content.source',
      'detail.content.created',
    ]);
  });

  it('房號列帶房號與樓層', () => {
    expect(valueOf(0)).toBe('detail.content.roomValue|305|3');
  });

  it('住客列寫名字與住期 M/D–M/D，月日不補零', () => {
    expect(valueOf(1, { guest: { name: '陳怡安', checkinDate: '2026-09-05', checkoutDate: '2026-10-27' } })).toBe(
      'detail.content.guestValue|陳怡安|9/5|10/27',
    );
  });

  it('空房的住客列寫「空房」', () => {
    expect(valueOf(1, { guest: null, guestName: null })).toBe('detail.content.vacant');
  });

  it('部門列寫部門與類型', () => {
    expect(valueOf(2, { dept: 'engineering', type: 'repair' })).toBe(
      'detail.content.deptValue|board.card.depts.engineering|board.card.types.repair',
    );
  });

  it('有負責人時寫負責人名字', () => {
    expect(valueOf(3)).toBe('林美玲');
  });

  it('待認領又沒有負責人時寫「等同仁認領」', () => {
    expect(valueOf(3, { state: 'pool', assigneeName: null })).toBe('detail.content.awaitingClaim');
  });

  it('其他狀況沒有負責人時寫「—」', () => {
    expect(valueOf(3, { state: 'triage', assigneeName: null })).toBe('detail.content.noAssignee');
  });

  it('住客掃碼的來源只有來源名稱', () => {
    expect(valueOf(4, { source: 'guest' })).toBe('detail.content.sources.guest');
  });

  it('系統排房的來源只有來源名稱', () => {
    expect(valueOf(4, { source: 'system', createdByName: null })).toBe('detail.content.sources.system');
  });

  it('櫃檯代開的來源在括號裡寫開單的人', () => {
    expect(valueOf(4, { source: 'desk', createdByName: '王小明' })).toBe('detail.content.sources.desk|王小明');
  });

  it('同仁報修的來源在括號裡寫報修的人', () => {
    expect(valueOf(4, { source: 'staff', createdByName: '張志豪' })).toBe('detail.content.sources.staff|張志豪');
  });

  it('建立列照伺服器時區寫時:分，未滿 1 分鐘寫「剛剛」', () => {
    expect(valueOf(5, {}, minutesAfter(0.9))).toBe('detail.content.createdValue|14:00|detail.justNow');
  });

  it('建立列滿 1 分鐘起寫「N 分鐘前」，無條件捨去', () => {
    expect(valueOf(5, {}, minutesAfter(12.7))).toBe('detail.content.createdValue|14:00|detail.minutesAgo|12');
  });
});

describe('createdAgoText', () => {
  it('還沒有校正後的現在時寫「剛剛」', () => {
    expect(createdAgoText(task(), undefined, t)).toBe('detail.justNow');
  });

  it('現在比建立時間早（時鐘誤差）時也寫「剛剛」，不會出現負的分鐘數', () => {
    expect(createdAgoText(task(), minutesAfter(-3), t)).toBe('detail.justNow');
  });
});
