import { QueryClient } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, NetworkError } from '../../api/client';
import { deskBoardQuery, receiveCardPush } from '../../api/desk';
import type { DeskBoard, DeskTask } from '../../api/types';
import { useDispatchStore } from '../../stores/dispatch';
import { assignTaskFlow, undoAssignFlow, UNDO_WAIT_MS } from './use-dispatch';

const assignDeskTask = vi.hoisted(() => vi.fn());
const undoDeskAssign = vi.hoisted(() => vi.fn());

vi.mock('../../api/desk', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/desk')>()),
  assignDeskTask,
  undoDeskAssign,
}));

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
    title: '房間有怪聲',
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

beforeEach(() => {
  assignDeskTask.mockReset();
  undoDeskAssign.mockReset();
  useDispatchStore.getState().reset();
});

describe('assignTaskFlow', () => {
  it('via 與部門原樣送給後端，按鈕與拖曳都一樣', async () => {
    const { queryClient } = setup([task('a'), task('b')]);
    assignDeskTask.mockImplementation(async (id: string) => task(id, { state: 'pool', dept: 'engineering' }));
    await assignTaskFlow(queryClient, task('a'), 'engineering', 'drag');
    await assignTaskFlow(queryClient, task('b'), 'housekeeping', 'button');
    expect(assignDeskTask).toHaveBeenNthCalledWith(1, 'a', { dept: 'engineering', via: 'drag' });
    expect(assignDeskTask).toHaveBeenNthCalledWith(2, 'b', { dept: 'housekeeping', via: 'button' });
  });

  it('派單失敗：這張單還原成按下前的樣子、浮條收掉，其他單不動（斷線重讀不到也一樣）', async () => {
    const { queryClient, tasksNow } = setup([task('a'), task('b')]);
    assignDeskTask.mockRejectedValue(new NetworkError(new TypeError('failed')));
    await assignTaskFlow(queryClient, task('a'), 'engineering', 'button');
    expect(tasksNow().map((item) => [item.id, item.state])).toEqual([
      ['a', 'triage'],
      ['b', 'triage'],
    ]);
    expect(useDispatchStore.getState().toast).toBeNull();
    expect(useDispatchStore.getState().notice).toEqual({ kind: 'assignNotSent', roomNo: '305' });
  });

  it('撞單：照後端說的移出待派單（被當場回答就是已完成，不帶剛選的部門）、浮條收掉、跳撞單的對話框', async () => {
    const { queryClient, tasksNow } = setup([task('a'), task('b')]);
    assignDeskTask.mockRejectedValue(
      new ApiError(409, {
        code: 'task_not_in_triage',
        message: '',
        context: { resolution: 'answered', actorName: '何志偉', roomNo: '305' },
      }),
    );
    await assignTaskFlow(queryClient, task('a'), 'housekeeping', 'button');
    expect(tasksNow().map((item) => [item.id, item.state, item.dept, item.answered])).toEqual([
      ['a', 'done', 'frontdesk', true],
      ['b', 'triage', 'frontdesk', false],
    ]);
    expect(useDispatchStore.getState().toast).toBeNull();
    expect(useDispatchStore.getState().notice).toEqual({
      kind: 'assignConflict',
      roomNo: '305',
      context: { resolution: 'answered', actorName: '何志偉', roomNo: '305' },
    });
  });

  it('撞單：被別人派走時放進對方派的部門，不是自己剛選的', async () => {
    const { queryClient, tasksNow } = setup([task('a')]);
    assignDeskTask.mockRejectedValue(
      new ApiError(409, {
        code: 'task_not_in_triage',
        message: '',
        context: { resolution: 'assigned', actorName: '何志偉', dept: 'engineering', roomNo: '305' },
      }),
    );
    await assignTaskFlow(queryClient, task('a'), 'housekeeping', 'button');
    expect(tasksNow().map((item) => [item.state, item.dept])).toEqual([['pool', 'engineering']]);
  });
});

describe('派單與即時訊號帶來的卡片', () => {
  it('派單的回應比訊號帶來的較新卡片晚到時，不把畫面倒回去', async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(deskBoardQuery.queryKey, {
      businessDate: '2026-10-26',
      tasks: [task('a', { seq: 1 })],
    } as unknown as DeskBoard);
    let respond: (assigned: DeskTask) => void = () => {};
    assignDeskTask.mockImplementation(() => new Promise<DeskTask>((resolve) => (respond = resolve)));
    const flow = assignTaskFlow(queryClient, task('a', { seq: 1 }), 'housekeeping', 'button');
    await vi.waitFor(() => expect(assignDeskTask).toHaveBeenCalled());

    const claimed = task('a', { seq: 3, state: 'accepted', dept: 'housekeeping', assigneeName: '小美' });
    receiveCardPush(queryClient, {
      taskId: 'a',
      seq: 3,
      cardSeq: 3,
      businessDate: '2026-10-26',
      change: { kind: 'upsert', card: claimed },
    });
    respond(task('a', { seq: 2, state: 'pool', dept: 'housekeeping' }));
    await flow;

    const tasks = (queryClient.getQueryData(deskBoardQuery.queryKey) as DeskBoard).tasks;
    expect(tasks[0]).toMatchObject({ seq: 3, state: 'accepted' });
  });

  it('按下派單之後，同一步的重複訊號不會把先畫的樣子換回待派單', async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(deskBoardQuery.queryKey, {
      businessDate: '2026-10-26',
      tasks: [task('a', { seq: 1 })],
    } as unknown as DeskBoard);
    assignDeskTask.mockImplementation(() => new Promise<DeskTask>(() => {}));
    void assignTaskFlow(queryClient, task('a', { seq: 1 }), 'housekeeping', 'button');
    await vi.waitFor(() => expect(assignDeskTask).toHaveBeenCalled());

    receiveCardPush(queryClient, {
      taskId: 'a',
      seq: 1,
      cardSeq: 1,
      businessDate: '2026-10-26',
      change: { kind: 'upsert', card: task('a', { seq: 1 }) },
    });

    const tasks = (queryClient.getQueryData(deskBoardQuery.queryKey) as DeskBoard).tasks;
    expect(tasks[0]).toMatchObject({ state: 'pool', dept: 'housekeeping' });
  });
});

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('undoAssignFlow', () => {
  const toast = { taskId: 'a', roomNo: '305', dept: 'housekeeping' as const };

  it('派單成功才送復原', async () => {
    const { queryClient } = setup([task('a')]);
    assignDeskTask.mockResolvedValue(task('a', { state: 'pool', dept: 'housekeeping' }));
    undoDeskAssign.mockResolvedValue(task('a'));
    const assigning = assignTaskFlow(queryClient, task('a'), 'housekeeping', 'button');
    await flush();
    await undoAssignFlow(queryClient, toast);
    await assigning;
    expect(undoDeskAssign).toHaveBeenCalledWith('a');
  });

  it('派單失敗不送復原', async () => {
    const { queryClient } = setup([task('a')]);
    let fail: (error: unknown) => void = () => {};
    assignDeskTask.mockReturnValue(new Promise((_resolve, reject) => (fail = reject)));
    const assigning = assignTaskFlow(queryClient, task('a'), 'housekeeping', 'button');
    await flush();
    const undoing = undoAssignFlow(queryClient, toast);
    fail(new NetworkError(new TypeError('failed')));
    await undoing;
    await assigning;
    expect(undoDeskAssign).not.toHaveBeenCalled();
  });

  it('派單卡住：等到逾時就照常送復原', async () => {
    vi.useFakeTimers();
    try {
      const { queryClient } = setup([task('a')]);
      assignDeskTask.mockReturnValue(new Promise(() => {}));
      undoDeskAssign.mockResolvedValue(task('a'));
      void assignTaskFlow(queryClient, task('a'), 'housekeeping', 'button');
      await vi.advanceTimersByTimeAsync(0);
      const undoing = undoAssignFlow(queryClient, toast);
      await vi.advanceTimersByTimeAsync(UNDO_WAIT_MS - 1);
      expect(undoDeskAssign).not.toHaveBeenCalled();
      await vi.advanceTimersByTimeAsync(1);
      await undoing;
      expect(undoDeskAssign).toHaveBeenCalledWith('a');
    } finally {
      vi.useRealTimers();
    }
  });
});
