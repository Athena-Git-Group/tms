import { describe, expect, it } from 'vitest';
import type { DeskBoard, DeskTask } from '../api/types';
import {
  BUSINESS_DAY_RETRY_MS,
  applyCardPush,
  businessDayRereadDelayMs,
  mergeFullRead,
  type CardPush,
  type ReceivedPushes,
} from './board-sync';

const TODAY = '2026-11-18';

function card(id: string, seq: number, overrides: Partial<DeskTask> = {}): DeskTask {
  return {
    id,
    seq,
    displayNo: `T-${id}`,
    state: 'pool',
    answered: false,
    dept: 'housekeeping',
    type: 'amenity',
    source: 'guest',
    roomNo: id,
    floor: 4,
    vip: false,
    title: '加兩瓶水',
    guestTyped: false,
    createdAt: '2026-11-18T08:00:00+08:00',
    updatedAt: '2026-11-18T08:00:00+08:00',
    ...overrides,
  };
}

function board(tasks: DeskTask[], businessDate = TODAY): DeskBoard {
  return {
    serverTime: `${businessDate}T09:00:00+08:00`,
    businessDate,
    floors: [4],
    tasks,
    myToday: { assignedCount: 0, answeredCount: 0 },
  };
}

function upsert(next: DeskTask, seq = next.seq, businessDate = TODAY): CardPush {
  return { taskId: next.id, seq, cardSeq: next.seq, businessDate, change: { kind: 'upsert', card: next } };
}

function remove(taskId: string, seq: number, businessDate = TODAY): CardPush {
  return { taskId, seq, cardSeq: seq, businessDate, change: { kind: 'removedForBusinessDay' } };
}

const none: ReceivedPushes = new Map();

function stateOf(result: { board: DeskBoard }, id: string) {
  return result.board.tasks.find((task) => task.id === id)?.state;
}

describe('applyCardPush', () => {
  it('比畫面上新的卡片直接換上，不必整份重讀', () => {
    const result = applyCardPush(board([card('412', 3)]), none, upsert(card('412', 4, { state: 'accepted' })), 1);
    expect(stateOf(result, '412')).toBe('accepted');
    expect(result.needsFullRead).toBe(false);
  });

  it.each([3, 2])('畫面上是第 3 步，又收到第 %i 步的卡片時不理', (step) => {
    const before = board([card('412', 3)]);
    const result = applyCardPush(before, none, upsert(card('412', step, { state: 'accepted' })), 1);
    expect(result.board).toBe(before);
    expect(result.needsFullRead).toBe(false);
  });

  it('畫面上沒有的單直接放上', () => {
    const result = applyCardPush(board([card('412', 3)]), none, upsert(card('305', 1, { state: 'triage' })), 1);
    expect(result.board.tasks.map((task) => task.id)).toEqual(['412', '305']);
    expect(result.needsFullRead).toBe(false);
  });

  it('後端說不在看板上時拿掉', () => {
    const result = applyCardPush(board([card('208', 3)]), none, remove('208', 4), 1);
    expect(result.board.tasks).toEqual([]);
    expect(result.needsFullRead).toBe(false);
  });

  it('拿掉之後晚到的舊卡片不會把它放回來', () => {
    const removed = applyCardPush(board([card('208', 3)]), none, remove('208', 4), 1);
    const late = applyCardPush(removed.board, removed.pushes, upsert(card('208', 4)), 2);
    expect(late.board.tasks).toEqual([]);
  });

  it('畫面上沒有的單說它不在看板上時不理，但記住步數', () => {
    const before = board([]);
    const removed = applyCardPush(before, none, remove('999', 5), 1);
    expect(removed.board).toBe(before);
    const late = applyCardPush(removed.board, removed.pushes, upsert(card('999', 5)), 2);
    expect(late.board.tasks).toEqual([]);
  });

  it('步數跳號時換上卡片，同時要整份重讀', () => {
    const result = applyCardPush(board([card('412', 3)]), none, upsert(card('412', 5, { state: 'accepted' })), 1);
    expect(stateOf(result, '412')).toBe('accepted');
    expect(result.needsFullRead).toBe(true);
  });

  it('同一交易寫了兩步：第 N 步的訊號帶第 N+1 步的卡片換上，之後第 N+1 步的訊號不理也不算跳號', () => {
    const first = applyCardPush(board([card('301', 3)]), none, upsert(card('301', 5, { state: 'done' }), 4), 1);
    expect(stateOf(first, '301')).toBe('done');
    expect(first.needsFullRead).toBe(false);
    const second = applyCardPush(first.board, first.pushes, upsert(card('301', 5, { state: 'done' }), 5), 2);
    expect(second.board).toBe(first.board);
    expect(second.needsFullRead).toBe(false);
    const third = applyCardPush(second.board, second.pushes, upsert(card('301', 6), 6), 3);
    expect(third.needsFullRead).toBe(false);
  });

  it('營業日和看板不同時不換，要整份重讀', () => {
    const before = board([card('412', 3)]);
    const result = applyCardPush(before, none, upsert(card('412', 4), 4, '2026-11-19'), 1);
    expect(result.board).toBe(before);
    expect(result.needsFullRead).toBe(true);
  });
});

describe('mergeFullRead', () => {
  it('讀取期間收到的較新卡片，不被讀回來的舊資料蓋掉', () => {
    const pushed = applyCardPush(board([card('412', 3)]), none, upsert(card('412', 4, { state: 'accepted' })), 2);
    const merged = mergeFullRead(board([card('412', 3)]), pushed.pushes, 1);
    expect(stateOf(merged, '412')).toBe('accepted');
  });

  it('讀取期間收到「不在看板上」，讀回來的舊資料裡仍有它時照樣拿掉', () => {
    const pushed = applyCardPush(board([card('208', 3)]), none, remove('208', 4), 2);
    const merged = mergeFullRead(board([card('208', 3)]), pushed.pushes, 1);
    expect(merged.board.tasks).toEqual([]);
  });

  it('讀取期間放上的新單，讀回來的那份還沒有它時保留', () => {
    const pushed = applyCardPush(board([]), none, upsert(card('305', 1, { state: 'triage' })), 2);
    const merged = mergeFullRead(board([]), pushed.pushes, 1);
    expect(merged.board.tasks.map((task) => task.id)).toEqual(['305']);
  });

  it('讀回來的比讀取期間收到的新時用讀回來的', () => {
    const pushed = applyCardPush(board([card('412', 3)]), none, upsert(card('412', 4, { state: 'accepted' })), 2);
    const merged = mergeFullRead(board([card('412', 5, { state: 'doing' })]), pushed.pushes, 1);
    expect(stateOf(merged, '412')).toBe('doing');
  });

  it('讀取開始前收到的卡片以讀回來的為準', () => {
    const pushed = applyCardPush(board([card('412', 3)]), none, upsert(card('412', 4, { state: 'accepted' })), 1);
    const fresh = board([]);
    const merged = mergeFullRead(fresh, pushed.pushes, 1);
    expect(merged.board).toBe(fresh);
    expect(merged.pushes.size).toBe(0);
  });

  it('讀取開始前記下的「不在看板上」留到換日，晚到的舊卡片之後也放不回來', () => {
    const removed = applyCardPush(board([card('208', 3)]), none, remove('208', 4), 1);
    const merged = mergeFullRead(board([]), removed.pushes, 1);
    const late = applyCardPush(merged.board, merged.pushes, upsert(card('208', 4)), 2);
    expect(late.board.tasks).toEqual([]);
    expect(mergeFullRead(board([], '2026-11-19'), merged.pushes, 2).pushes.size).toBe(0);
  });

  it('營業日換了，讀取期間收到的舊營業日卡片不保留', () => {
    const pushed = applyCardPush(board([card('301', 3)]), none, upsert(card('301', 4, { state: 'done' })), 2);
    const merged = mergeFullRead(board([], '2026-11-19'), pushed.pushes, 1);
    expect(merged.board.tasks).toEqual([]);
  });
});

describe('businessDayRereadDelayMs', () => {
  const at = (iso: string) => Date.parse(iso);

  it('等到 businessDate 隔天 00:00（照 serverTime 的時差）', () => {
    const delay = businessDayRereadDelayMs(
      { businessDate: '2026-11-18', serverTime: '2026-11-18T23:58:00+08:00' },
      at('2026-11-18T23:58:00+08:00'),
    );
    expect(delay).toBe(2 * 60_000);
  });

  it('時差是負的也照伺服器時區算', () => {
    const delay = businessDayRereadDelayMs(
      { businessDate: '2026-11-18', serverTime: '2026-11-18T23:00:00-05:00' },
      at('2026-11-18T23:00:00-05:00'),
    );
    expect(delay).toBe(60 * 60_000);
  });

  it('已經過了換日時刻、讀回來還是舊營業日時一分鐘後再讀', () => {
    const delay = businessDayRereadDelayMs(
      { businessDate: '2026-11-18', serverTime: '2026-11-18T23:59:59+08:00' },
      at('2026-11-19T00:00:03+08:00'),
    );
    expect(delay).toBe(BUSINESS_DAY_RETRY_MS);
  });

  it('serverTime 讀不出時差時不排', () => {
    expect(businessDayRereadDelayMs({ businessDate: '2026-11-18', serverTime: '2026-11-18T23:58:00' }, 0)).toBeNull();
  });
});
