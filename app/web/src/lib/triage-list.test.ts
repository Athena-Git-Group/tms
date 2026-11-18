import { describe, expect, it } from 'vitest';
import type { DeskTask, TaskState } from '../api/types';
import { poolCount, triageTasks } from './triage-list';

function task(state: TaskState, overrides: Partial<DeskTask> = {}): DeskTask {
  return {
    id: '1',
    displayNo: 'T-1',
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
    createdAt: '2026-10-26T06:00:00Z',
    updatedAt: '2026-10-26T06:00:00Z',
    ...overrides,
  };
}

describe('triageTasks', () => {
  it('只留待派單，最新進來的在最上面', () => {
    const tasks = [
      task('triage', { id: 'old', createdAt: '2026-10-26T01:00:00Z' }),
      task('pool', { id: 'pool' }),
      task('triage', { id: 'new', createdAt: '2026-10-26T03:00:00Z' }),
      task('doing', { id: 'doing' }),
    ];
    expect(triageTasks(tasks).map((t) => t.id)).toEqual(['new', 'old']);
  });

  it('沒有待派單回空陣列', () => {
    expect(triageTasks([task('pool'), task('done')])).toEqual([]);
  });
});

describe('poolCount', () => {
  const tasks = [
    task('pool', { id: 'h1', dept: 'housekeeping' }),
    task('pool', { id: 'h2', dept: 'housekeeping' }),
    task('pool', { id: 'e1', dept: 'engineering' }),
    task('triage', { id: 'h3', dept: 'housekeeping' }),
    task('doing', { id: 'e2', dept: 'engineering' }),
  ];

  it('房務、工務分開算，只算待認領', () => {
    expect(poolCount(tasks, 'housekeeping')).toBe(2);
    expect(poolCount(tasks, 'engineering')).toBe(1);
  });

  it('沒有待認領算 0', () => {
    expect(poolCount([task('triage')], 'housekeeping')).toBe(0);
  });
});
