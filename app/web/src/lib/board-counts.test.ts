import { describe, expect, it } from 'vitest';
import type { DeskTask, TaskState } from '../api/types';
import { BOARD_COLUMNS } from './board-columns';
import { columnHeads, tileCounts } from './board-counts';

const now = new Date('2026-10-26T06:30:00Z');

function ago(minutesAgo: number): string {
  return new Date(now.getTime() - minutesAgo * 60_000).toISOString();
}

function task(id: string, state: TaskState, overrides: Partial<DeskTask> = {}): DeskTask {
  return {
    id,
    displayNo: `T-${id}`,
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
    createdAt: ago(5),
    updatedAt: ago(5),
    ...overrides,
  };
}

function headOf(tasks: DeskTask[], column: (typeof BOARD_COLUMNS)[number]) {
  return columnHeads(tasks, BOARD_COLUMNS, now).find((head) => head.column === column)!;
}

describe('tileCounts', () => {
  it('四張卡：待派單、待認領（分房務工務）、處理中（已接加進行中），已結案與其他欄不算', () => {
    const tasks = [
      task('1', 'triage'),
      task('2', 'triage'),
      task('3', 'pool', { dept: 'housekeeping' }),
      task('4', 'pool', { dept: 'engineering' }),
      task('5', 'pool', { dept: 'engineering' }),
      task('6', 'accepted'),
      task('7', 'doing'),
      task('8', 'done'),
      task('9', 'interrupted'),
      task('10', 'deferred'),
      task('11', 'closed'),
    ];
    const counts = tileCounts(tasks, now);
    expect(counts.triage).toBe(2);
    expect(counts.pool).toBe(3);
    expect(counts.poolByDept).toEqual({ housekeeping: 1, engineering: 2 });
    expect(counts.inProgress).toBe(2);
  });

  it('等最久從待派單與待認領挑起算時刻最早的，起算時刻用 waitingSince，沒有才用建立時間', () => {
    const tasks = [
      task('early-accepted', 'accepted', { createdAt: ago(90) }),
      task('triage', 'triage', { createdAt: ago(12) }),
      task('pool', 'pool', { createdAt: ago(60), waitingSince: ago(22) }),
    ];
    const longest = tileCounts(tasks, now).longest;
    expect(longest?.task.id).toBe('pool');
    expect(longest?.minutes).toBe(22);
    expect(longest?.tone).toBe('red');
  });

  it.each([
    [9, 'gray'],
    [10, 'orange'],
    [19, 'orange'],
    [20, 'red'],
  ] as const)('等最久等了 %i 分是 %s', (minutes, tone) => {
    expect(tileCounts([task('a', 'triage', { createdAt: ago(minutes) })], now).longest?.tone).toBe(tone);
  });

  it('沒有在等的單時等最久是 null', () => {
    expect(tileCounts([task('a', 'accepted'), task('b', 'done')], now).longest).toBeNull();
    expect(tileCounts([], now).longest).toBeNull();
  });

  it('還沒有校正後的現在時，等最久先不給分鐘數', () => {
    const longest = tileCounts([task('a', 'pool', { createdAt: ago(30) })], undefined).longest;
    expect(longest?.task.id).toBe('a');
    expect(longest?.minutes).toBeUndefined();
    expect(longest?.tone).toBe('gray');
  });
});

describe('columnHeads', () => {
  it('依傳進來的欄順序回各欄，張數與欄內單一致', () => {
    const heads = columnHeads(
      [task('1', 'accepted'), task('2', 'doing'), task('3', 'doing')],
      ['accepted', 'doing'],
      now,
    );
    expect(heads.map((head) => [head.column, head.count, head.tasks.length])).toEqual([
      ['accepted', 1, 1],
      ['doing', 2, 2],
    ]);
  });

  it('待派單、待認領等了 20 分以上的單才算「逾 20 分」，19 分不算', () => {
    const tasks = [
      task('t19', 'triage', { createdAt: ago(19) }),
      task('t20', 'triage', { createdAt: ago(20) }),
      task('p19', 'pool', { waitingSince: ago(19) }),
      task('p20', 'pool', { waitingSince: ago(20) }),
      task('p40', 'pool', { waitingSince: ago(40) }),
    ];
    expect(headOf(tasks, 'triage').warning).toEqual({ kind: 'overdue', count: 1 });
    expect(headOf(tasks, 'pool').warning).toEqual({ kind: 'overdue', count: 2 });
  });

  it('都沒有等到 20 分時沒有警示', () => {
    expect(headOf([task('t19', 'triage', { createdAt: ago(19) })], 'triage').warning).toBeNull();
  });

  it('已接與進行中等再久都沒有警示', () => {
    const tasks = [task('a', 'accepted', { createdAt: ago(90) }), task('d', 'doing', { createdAt: ago(120) })];
    expect(headOf(tasks, 'accepted').warning).toBeNull();
    expect(headOf(tasks, 'doing').warning).toBeNull();
  });

  it('已完成欄只算打掃單為「待查房」；當場回答過的單在已結案欄，不算', () => {
    const tasks = [
      task('c1', 'done', { type: 'cleaning' }),
      task('c2', 'done', { type: 'cleaning' }),
      task('a1', 'done', { type: 'amenity' }),
      task('ans', 'done', { type: 'cleaning', answered: true }),
    ];
    expect(headOf(tasks, 'done').warning).toEqual({ kind: 'awaitingInspection', count: 2 });
    expect(headOf([task('a1', 'done', { type: 'amenity' })], 'done').warning).toBeNull();
  });

  it('還沒有校正後的現在時不顯示「逾 20 分」', () => {
    const heads = columnHeads([task('t', 'triage', { createdAt: ago(30) })], ['triage'], undefined);
    expect(heads[0]?.warning).toBeNull();
  });
});

describe('數量卡與欄頭的範圍', () => {
  it('搜尋房號之後欄頭數字跟著變，數量卡仍是全館', () => {
    const hotel = [
      task('1', 'pool', { roomNo: '305', waitingSince: ago(25) }),
      task('2', 'pool', { roomNo: '412', waitingSince: ago(30) }),
      task('3', 'triage', { roomNo: '418' }),
    ];
    const searched = hotel.filter((one) => one.roomNo.startsWith('30'));

    const tiles = tileCounts(hotel, now);
    expect(tiles.pool).toBe(2);
    expect(tiles.triage).toBe(1);
    expect(tiles.longest?.task.roomNo).toBe('412');

    expect(headOf(hotel, 'pool').count).toBe(2);
    expect(headOf(hotel, 'pool').warning).toEqual({ kind: 'overdue', count: 2 });
    expect(headOf(searched, 'pool').count).toBe(1);
    expect(headOf(searched, 'pool').warning).toEqual({ kind: 'overdue', count: 1 });
    expect(headOf(searched, 'triage').count).toBe(0);
  });
});
