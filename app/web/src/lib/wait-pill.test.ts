import { describe, expect, it } from 'vitest';
import type { DeskTask, TaskState } from '../api/types';
import i18next from 'i18next';
import { zhTW } from '../i18n/locales/zh-TW';
import { displayString } from './display-text';
import { clockTime, waitingMinutes, waitPill, waitTone } from './wait-pill';

function task(state: TaskState, overrides: Partial<DeskTask> = {}): DeskTask {
  return {
    id: 'a',
    displayNo: 'T-0001',
    seq: 1,
    state,
    answered: false,
    dept: 'housekeeping',
    type: 'amenity',
    source: 'guest',
    roomNo: '305',
    floor: 3,
    vip: false,
    title: '多一條浴巾',
    guestTyped: false,
    createdAt: '2026-10-19T04:00:00Z',
    updatedAt: '2026-10-19T04:00:00Z',
    ...overrides,
  };
}

const zh = i18next.createInstance();
void zh.init({
  lng: 'zh-TW',
  resources: { 'zh-TW': { translation: zhTW } },
  initAsync: false,
  interpolation: { escapeValue: false },
});
const t = zh.t;

function shownPill(...args: Parameters<typeof waitPill>): { text: string; tone: string } {
  const pill = waitPill(...args);
  return { text: displayString(pill.text, t), tone: pill.tone };
}

describe('waitingMinutes', () => {
  const now = new Date('2026-10-26T06:10:00Z');

  it('從 waitingSince 算起，無條件捨去', () => {
    const waiting = task('pool', {
      createdAt: '2026-10-26T05:00:00Z',
      waitingSince: '2026-10-26T06:00:30Z',
    });
    expect(waitingMinutes(waiting, now)).toBe(9);
  });

  it('剛好整 20 分算 20 分', () => {
    expect(waitingMinutes(task('triage', { waitingSince: '2026-10-26T05:50:00Z' }), now)).toBe(20);
  });

  it('沒有 waitingSince 時從建立時間算起', () => {
    expect(waitingMinutes(task('triage', { createdAt: '2026-10-26T05:55:00Z' }), now)).toBe(15);
  });

  it('起算時刻在現在之後時是 0', () => {
    expect(waitingMinutes(task('pool', { waitingSince: '2026-10-26T06:11:00Z' }), now)).toBe(0);
  });

  it.each(['accepted', 'doing', 'done', 'interrupted', 'deferred', 'inspected', 'closed', 'cancelled'] as const)(
    '%s 的單不在等，回 null',
    (state) => {
      expect(waitingMinutes(task(state, { createdAt: '2026-10-26T05:00:00Z' }), now)).toBeNull();
    },
  );
});

describe('waitTone', () => {
  it.each([
    [0, 'gray'],
    [9, 'gray'],
    [10, 'orange'],
    [19, 'orange'],
    [20, 'red'],
    [45, 'red'],
  ] as const)('%i 分是 %s', (minutes, tone) => {
    expect(waitTone(minutes)).toBe(tone);
  });
});

describe('waitPill', () => {
  const now = new Date('2026-10-26T06:10:00Z');
  const minutesAgo = (minutes: number) => new Date(now.getTime() - minutes * 60_000).toISOString();
  const TAIPEI = 480;

  describe('待派單：不到 1 分「剛剛」，之後「等 N 分」，等再久都不寫逾時', () => {
    it.each([
      [0, '剛剛', 'gray'],
      [1, '等 1 分', 'gray'],
      [9, '等 9 分', 'gray'],
      [10, '等 10 分', 'orange'],
      [12, '等 12 分', 'orange'],
      [20, '等 20 分', 'red'],
      [23, '等 23 分', 'red'],
    ] as const)('等了 %i 分', (minutes, text, tone) => {
      expect(shownPill(task('triage', { waitingSince: minutesAgo(minutes) }), now, TAIPEI)).toEqual({ text, tone });
    });

    it('沒有 waitingSince 時從建立時間算起', () => {
      expect(shownPill(task('triage', { createdAt: minutesAgo(25) }), now, TAIPEI)).toEqual({
        text: '等 25 分',
        tone: 'red',
      });
    });
  });

  describe('待認領：10 分起寫「逾時 · N 分」，N 是超過 10 分的部分', () => {
    it.each([
      [0, '剛剛', 'gray'],
      [1, '等 1 分', 'gray'],
      [9, '等 9 分', 'gray'],
      [10, '逾時 · 0 分', 'orange'],
      [12, '逾時 · 2 分', 'orange'],
      [20, '逾時 · 10 分', 'red'],
      [23, '逾時 · 13 分', 'red'],
    ] as const)('等了 %i 分', (minutes, text, tone) => {
      expect(shownPill(task('pool', { waitingSince: minutesAgo(minutes) }), now, TAIPEI)).toEqual({ text, tone });
    });
  });

  it('班前排房指派的已接寫「今日排房」灰色', () => {
    expect(
      shownPill(task('accepted', { acceptedVia: 'plan_assigned', acceptedAt: minutesAgo(90) }), now, TAIPEI),
    ).toEqual({ text: '今日排房', tone: 'gray' });
  });

  it('其他方式的已接寫「接 N 分」灰色，不判斷逾時', () => {
    expect(
      shownPill(task('accepted', { acceptedVia: 'self_claimed', acceptedAt: minutesAgo(45) }), now, TAIPEI),
    ).toEqual({ text: '接 45 分', tone: 'gray' });
  });

  it('進行中寫「做 N 分」灰色', () => {
    expect(shownPill(task('doing', { startedAt: minutesAgo(120) }), now, TAIPEI)).toEqual({
      text: '做 120 分',
      tone: 'gray',
    });
  });

  it('中斷寫「停 N 分」紅色', () => {
    expect(shownPill(task('interrupted', { interruptedAt: minutesAgo(15) }), now, TAIPEI)).toEqual({
      text: '停 15 分',
      tone: 'red',
    });
  });

  it('延後中一律「待重排」灰色', () => {
    expect(shownPill(task('deferred', { updatedAt: minutesAgo(300) }), now, TAIPEI)).toEqual({
      text: '待重排',
      tone: 'gray',
    });
  });

  it('已完成寫伺服器時區的完成時刻、綠色', () => {
    expect(shownPill(task('done', { doneAt: '2026-10-26T06:20:00Z' }), now, TAIPEI)).toEqual({
      text: '14:20',
      tone: 'green',
    });
  });

  it('已結案（含查房通過）寫伺服器時區的結束時刻、灰色', () => {
    expect(shownPill(task('inspected', { endedAt: '2026-10-26T01:05:00Z' }), now, TAIPEI)).toEqual({
      text: '09:05',
      tone: 'gray',
    });
  });

  it('當場回答過的已完成單在已結案欄，寫結束時刻、灰色', () => {
    expect(
      shownPill(
        task('done', { answered: true, doneAt: '2026-10-26T06:20:00Z', endedAt: '2026-10-26T06:25:00Z' }),
        now,
        TAIPEI,
      ),
    ).toEqual({ text: '14:25', tone: 'gray' });
  });

  it('完成當下就結束的備品單在已結案欄，寫結束時刻、灰色', () => {
    expect(
      shownPill(task('done', { type: 'amenity', doneAt: '2026-10-26T06:30:00Z', endedAt: '2026-10-26T06:30:00Z' }), now, TAIPEI),
    ).toEqual({ text: '14:30', tone: 'gray' });
  });

  describe('起算時刻後端沒給時，退回最後更新的時刻', () => {
    const updatedAt = minutesAgo(7);

    it('已接沒有 acceptedAt', () => {
      expect(shownPill(task('accepted', { acceptedVia: 'self_claimed', updatedAt }), now, TAIPEI)).toEqual({
        text: '接 7 分',
        tone: 'gray',
      });
    });

    it('進行中沒有 startedAt', () => {
      expect(shownPill(task('doing', { updatedAt }), now, TAIPEI).text).toEqual('做 7 分');
    });

    it('中斷沒有 interruptedAt', () => {
      expect(shownPill(task('interrupted', { updatedAt }), now, TAIPEI).text).toEqual('停 7 分');
    });

    it('已完成沒有 doneAt、已結案沒有 endedAt', () => {
      expect(shownPill(task('done', { updatedAt: '2026-10-26T06:03:00Z' }), now, TAIPEI).text).toEqual('14:03');
      expect(shownPill(task('closed', { updatedAt: '2026-10-26T06:03:00Z' }), now, TAIPEI).text).toEqual('14:03');
    });
  });
});

describe('clockTime', () => {
  const at = '2026-10-26T06:20:00Z';

  it('同一個時刻在 +08:00 與 UTC 的伺服器顯示不同的時:分', () => {
    expect(clockTime(at, 480)).toBe('14:20');
    expect(clockTime(at, 0)).toBe('06:20');
  });

  it('負的偏移往前推', () => {
    expect(clockTime(at, -330)).toBe('00:50');
  });

  it('跨日時照伺服器時區的那一天：UTC 23:30 在 +08:00 是 07:30', () => {
    expect(clockTime('2026-10-26T23:30:00Z', 480)).toBe('07:30');
  });

  it('帶偏移的時刻字串先換成同一個時刻再顯示', () => {
    expect(clockTime('2026-10-26T14:20:00+08:00', 480)).toBe('14:20');
    expect(clockTime('2026-10-26T14:20:00+08:00', 0)).toBe('06:20');
  });
});
