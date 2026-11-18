import { describe, expect, it } from 'vitest';
import type { DeskTask, TaskState } from '../api/types';
import { BOARD_COLUMNS, countOpenTasks } from './board-columns';
import { boardLayout, filterPillContent, roomSearchInput, searchByRoom } from './board-view-model';

const now = new Date('2026-10-26T06:30:00Z');

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
    createdAt: '2026-10-26T06:25:00Z',
    updatedAt: '2026-10-26T06:25:00Z',
    ...overrides,
  };
}

const none = { cardFilter: null, roomSearch: '', lane: 'none' } as const;

const tasks = [
  task('1', 'triage', { roomNo: '305', floor: 3, dept: 'frontdesk' }),
  task('2', 'pool', { roomNo: '3051', floor: 3, dept: 'housekeeping' }),
  task('3', 'doing', { roomNo: '812', floor: 8, dept: 'engineering' }),
  task('4', 'closed', { roomNo: '305', floor: 3, dept: 'housekeeping' }),
];

describe('roomSearchInput', () => {
  it('只留數字，最多 4 碼', () => {
    expect(roomSearchInput('3a0-5')).toBe('305');
    expect(roomSearchInput('30512')).toBe('3051');
    expect(roomSearchInput('abc')).toBe('');
  });
});

describe('searchByRoom', () => {
  it('房號以輸入的數字開頭才留下，沒輸入時全留', () => {
    expect(searchByRoom(tasks, '305').map((t) => t.id)).toEqual(['1', '2', '4']);
    expect(searchByRoom(tasks, '5')).toEqual([]);
    expect(searchByRoom(tasks, '')).toHaveLength(4);
  });
});

describe('boardLayout 分列', () => {
  it('不分泳道：沒有泳道列，欄頭算全部的單', () => {
    const layout = boardLayout(tasks, [3, 8], none, now);
    expect(layout.laneRows).toBeNull();
    expect(layout.columns).toEqual(BOARD_COLUMNS);
    expect(layout.heads.reduce((sum, head) => sum + head.count, 0)).toBe(4);
  });

  it('部門泳道固定三列：未分部門、房務、工務，沒有單的部門也有一列', () => {
    const layout = boardLayout([task('1', 'doing', { dept: 'housekeeping' })], [3], { ...none, lane: 'dept' }, now);
    expect(layout.laneRows!.map((row) => row.lane)).toEqual([
      { kind: 'dept', dept: 'frontdesk' },
      { kind: 'dept', dept: 'housekeeping' },
      { kind: 'dept', dept: 'engineering' },
    ]);
    expect(layout.laneRows!.map((row) => row.openCount)).toEqual([0, 1, 0]);
  });

  it('樓層泳道照飯店樓層由低到高，空樓層也有一列', () => {
    const layout = boardLayout(tasks, [8, 3, 5], { ...none, lane: 'floor' }, now);
    expect(layout.laneRows!.map((row) => row.key)).toEqual(['floor-3', 'floor-5', 'floor-8']);
    expect(layout.laneRows!.map((row) => row.openCount)).toEqual([2, 0, 1]);
    expect(layout.laneRows![1]!.heads.every((head) => head.count === 0)).toBe(true);
  });
});

describe('boardLayout 篩選與搜尋', () => {
  it('數量卡篩選只決定顯示的欄，泳道副標仍算搜尋後全部的未結案單', () => {
    const layout = boardLayout(tasks, [3, 8], { cardFilter: 'triage', roomSearch: '', lane: 'floor' }, now);
    expect(layout.columns).toEqual(['triage']);
    expect(layout.heads.map((head) => head.column)).toEqual(['triage']);
    const floor3 = layout.laneRows![0]!;
    expect(floor3.heads.map((head) => head.column)).toEqual(['triage']);
    expect(floor3.openCount).toBe(countOpenTasks(tasks.filter((t) => t.floor === 3)));
    expect(floor3.openCount).toBe(2);
  });

  it('房號搜尋同時改欄頭與泳道副標', () => {
    const layout = boardLayout(tasks, [3, 8], { cardFilter: null, roomSearch: '8', lane: 'floor' }, now);
    expect(layout.laneRows!.map((row) => row.openCount)).toEqual([0, 1]);
    expect(layout.heads.reduce((sum, head) => sum + head.count, 0)).toBe(1);
  });

  it('全館的單不被改動，頁副標用的未結案張數不受篩選與搜尋影響', () => {
    boardLayout(tasks, [3, 8], { cardFilter: 'pool', roomSearch: '812', lane: 'dept' }, now);
    expect(tasks).toHaveLength(4);
    expect(countOpenTasks(tasks)).toBe(3);
  });
});

describe('篩選膠囊', () => {
  it('都沒有時不顯示', () => {
    expect(filterPillContent(null, '')).toBeNull();
    expect(boardLayout(tasks, [], none, now).pill).toBeNull();
  });

  it('只有篩選', () => {
    expect(filterPillContent('pool', '')).toEqual({ filter: 'pool', roomNo: null });
  });

  it('只有搜尋', () => {
    expect(filterPillContent(null, '305')).toEqual({ filter: null, roomNo: '305' });
  });

  it('兩者都有', () => {
    const layout = boardLayout(tasks, [], { cardFilter: 'triage', roomSearch: '305', lane: 'none' }, now);
    expect(layout.pill).toEqual({ filter: 'triage', roomNo: '305' });
  });
});
