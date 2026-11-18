import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import { deskBoardQuery } from '../../api/desk';
import type { DeskBoard, DeskTask } from '../../api/types';
import { settleConflictOnBoard } from './settle-conflict';

function task(id: string, overrides: Partial<DeskTask> = {}): DeskTask {
  return {
    id,
    displayNo: `T-${id}`,
    seq: 1,
    state: 'triage',
    answered: false,
    dept: 'frontdesk',
    type: 'other',
    source: 'guest',
    roomNo: '305',
    floor: 3,
    vip: false,
    title: '早餐幾點',
    guestTyped: true,
    createdAt: '2026-10-26T06:00:00Z',
    updatedAt: '2026-10-26T06:00:00Z',
    ...overrides,
  };
}

function setup(tasks: DeskTask[]) {
  const queryClient = new QueryClient();
  queryClient.setQueryData(deskBoardQuery.queryKey, { tasks } as unknown as DeskBoard);
  const tasksNow = () => (queryClient.getQueryData(deskBoardQuery.queryKey) as DeskBoard).tasks;
  return { queryClient, tasksNow };
}

describe('settleConflictOnBoard', () => {
  it('當場回答撞單（沒有先畫）：直接照後端說的移出待派單，其他單不動', () => {
    const { queryClient, tasksNow } = setup([task('a'), task('b')]);
    settleConflictOnBoard(queryClient, 'a', { resolution: 'guest_withdrew' });
    expect(tasksNow().map((item) => [item.id, item.state])).toEqual([
      ['a', 'cancelled'],
      ['b', 'triage'],
    ]);
  });

  it('派單撞單（先畫成待認領）：先換回按下前的樣子再移，不帶櫃檯剛選的部門', () => {
    const before = task('a');
    const { queryClient, tasksNow } = setup([{ ...before, state: 'pool', dept: 'housekeeping' }]);
    settleConflictOnBoard(queryClient, 'a', { resolution: 'answered' }, before);
    expect(tasksNow()[0]).toMatchObject({ state: 'done', answered: true, dept: 'frontdesk' });
  });

  it('後端沒說是哪一種：派單這一側停在按下前的樣子等重讀', () => {
    const before = task('a');
    const { queryClient, tasksNow } = setup([{ ...before, state: 'pool', dept: 'housekeeping' }]);
    settleConflictOnBoard(queryClient, 'a', {}, before);
    expect(tasksNow()[0]).toEqual(before);
  });

  it('看板還沒讀過：什麼都不做', () => {
    const queryClient = new QueryClient();
    settleConflictOnBoard(queryClient, 'a', { resolution: 'answered' });
    expect(queryClient.getQueryData(deskBoardQuery.queryKey)).toBeUndefined();
  });
});
