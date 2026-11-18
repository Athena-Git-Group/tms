import { describe, expect, it } from 'vitest';
import type { DeskTask, TaskState } from '../api/types';
import {
  BOARD_COLUMNS,
  columnOf,
  columnsShownFor,
  countOpenTasks,
  sortInColumn,
} from './board-columns';

function task(state: TaskState, answered = false, overrides: Partial<DeskTask> = {}): DeskTask {
  return {
    id: 'a',
    displayNo: 'T-0001',
    state,
    answered,
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

describe('columnOf', () => {
  it.each([
    ['triage', 'triage'],
    ['pool', 'pool'],
    ['accepted', 'accepted'],
    ['doing', 'doing'],
    ['done', 'done'],
    ['interrupted', 'interrupted'],
    ['deferred', 'deferred'],
    ['inspected', 'closed'],
    ['closed', 'closed'],
    ['cancelled', 'closed'],
  ] as const)('狀態 %s 的單落在 %s 欄', (state, column) => {
    expect(columnOf(task(state))).toBe(column);
  });

  it('當場回答過的完成單落在已結案', () => {
    expect(columnOf(task('done', true))).toBe('closed');
  });
});

describe('countOpenTasks', () => {
  it('只算不在已結案欄的單，各欄混在一起也一樣', () => {
    const tasks = [
      task('triage'),
      task('pool'),
      task('accepted'),
      task('doing'),
      task('done'),
      task('interrupted'),
      task('deferred'),
      task('done', true),
      task('inspected'),
      task('closed'),
      task('cancelled'),
    ];
    expect(countOpenTasks(tasks)).toBe(7);
  });

  it('全部都結案時是 0', () => {
    expect(countOpenTasks([task('closed'), task('cancelled'), task('inspected'), task('done', true)])).toBe(0);
  });

  it('沒有單時是 0', () => {
    expect(countOpenTasks([])).toBe(0);
  });
});

describe('sortInColumn', () => {
  const ids = (tasks: DeskTask[]) => tasks.map((t) => t.id);

  it('待派單：最新進來的在上', () => {
    const tasks = [
      task('triage', false, { id: 'old', createdAt: '2026-10-19T01:00:00Z' }),
      task('triage', false, { id: 'new', createdAt: '2026-10-19T03:00:00Z' }),
      task('triage', false, { id: 'mid', createdAt: '2026-10-19T02:00:00Z' }),
    ];
    expect(ids(sortInColumn('triage', tasks))).toEqual(['new', 'mid', 'old']);
  });

  it('待認領：等最久的在上，沒有開始等待的時刻就拿建立時間比', () => {
    const tasks = [
      task('pool', false, { id: 'recent', waitingSince: '2026-10-19T03:00:00Z' }),
      task('pool', false, { id: 'fallback', waitingSince: null, createdAt: '2026-10-19T02:00:00Z' }),
      task('pool', false, { id: 'longest', waitingSince: '2026-10-19T01:00:00Z', createdAt: '2026-10-19T05:00:00Z' }),
      task('pool', false, { id: 'absent', createdAt: '2026-10-19T02:30:00Z' }),
    ];
    expect(ids(sortInColumn('pool', tasks))).toEqual(['longest', 'fallback', 'absent', 'recent']);
  });

  it('其他欄：最後更新最新的在上', () => {
    const tasks = [
      task('doing', false, { id: 'old', updatedAt: '2026-10-19T01:00:00Z' }),
      task('doing', false, { id: 'new', updatedAt: '2026-10-19T03:00:00Z' }),
      task('doing', false, { id: 'mid', updatedAt: '2026-10-19T02:00:00Z' }),
    ];
    for (const column of ['accepted', 'doing', 'done', 'interrupted', 'deferred', 'closed'] as const) {
      expect(ids(sortInColumn(column, tasks))).toEqual(['new', 'mid', 'old']);
    }
  });

  it('時刻相同時維持原本的順序', () => {
    const same = (id: string) => task('triage', false, { id });
    const tasks = [same('a'), same('b'), same('c')];
    expect(ids(sortInColumn('triage', tasks))).toEqual(['a', 'b', 'c']);
    expect(ids(sortInColumn('pool', tasks))).toEqual(['a', 'b', 'c']);
    expect(ids(sortInColumn('doing', tasks))).toEqual(['a', 'b', 'c']);
  });

  it('回傳新的陣列，不動傳進來的那一份', () => {
    const tasks = [
      task('triage', false, { id: 'old', createdAt: '2026-10-19T01:00:00Z' }),
      task('triage', false, { id: 'new', createdAt: '2026-10-19T03:00:00Z' }),
    ];
    const sorted = sortInColumn('triage', tasks);
    expect(sorted).not.toBe(tasks);
    expect(ids(tasks)).toEqual(['old', 'new']);
  });
});

describe('columnsShownFor', () => {
  it('沒有篩選時 8 欄都在', () => {
    expect(columnsShownFor(null)).toEqual(BOARD_COLUMNS);
  });

  it('處理中留下已接與進行中', () => {
    expect(columnsShownFor('in_progress')).toEqual(['accepted', 'doing']);
  });

  it('待派單、待認領各只留自己那一欄', () => {
    expect(columnsShownFor('triage')).toEqual(['triage']);
    expect(columnsShownFor('pool')).toEqual(['pool']);
  });
});
