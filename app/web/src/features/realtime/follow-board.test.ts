import { QueryClient, QueryObserver } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { deskBoardQuery } from '../../api/desk';
import type { DeskBoard, DeskTask } from '../../api/types';
import { useServerClockStore } from '../../stores/server-clock';
import { followBoard } from './follow-board';

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

function board(tasks: DeskTask[], businessDate = '2026-11-18', serverTime = '2026-11-18T09:00:00+08:00'): DeskBoard {
  return { serverTime, businessDate, floors: [4], tasks, myToday: { assignedCount: 0, answeredCount: 0 } };
}

/** 假後端：每次 `GET /desk/board` 都掛著，等測試給它要回的看板。 */
function fakeBackend() {
  const reads: Array<(body: DeskBoard) => void> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(
      (_url: string, init: RequestInit) =>
        new Promise<Response>((resolve, reject) => {
          init.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
          reads.push((body) => resolve(new Response(JSON.stringify(body))));
        }),
    ),
  );
  return reads;
}

/** 看板已經讀過一次、外框正看著它，串流的訊號交給 `followBoard`。 */
function shellWith(initial: DeskBoard) {
  const queryClient = new QueryClient();
  queryClient.setQueryData(deskBoardQuery.queryKey, initial);
  new QueryObserver(queryClient, { ...deskBoardQuery, staleTime: Infinity, retryOnMount: false }).subscribe(() => {});
  const follower = followBoard(queryClient);
  const send = (event: string, data: unknown = {}) =>
    follower.onStreamEvent({ event, data: typeof data === 'string' ? data : JSON.stringify(data) });
  const tasks = () => queryClient.getQueryData(deskBoardQuery.queryKey)!.tasks;
  return { queryClient, follower, send, tasks };
}

function upsertSignal(next: DeskTask, seq = next.seq, businessDate = '2026-11-18') {
  return { stream_pos: 1, task_id: next.id, seq, type: 'claimed', board: 'upsert', card_seq: next.seq, business_date: businessDate, card: next };
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('followBoard：訊號帶卡片', () => {
  it('較新的卡片直接換上，不重讀整份看板', async () => {
    const reads = fakeBackend();
    const { send, tasks, follower } = shellWith(board([card('412', 3)]));
    send('task', upsertSignal(card('412', 4, { state: 'accepted' })));
    await settle();
    expect(tasks()[0]).toMatchObject({ state: 'accepted', seq: 4 });
    expect(reads).toHaveLength(0);
    follower.stop();
  });

  it('沒帶卡片的訊號整份重讀', async () => {
    const reads = fakeBackend();
    const { send, follower } = shellWith(board([card('412', 3)]));
    send('task', { stream_pos: 1, task_id: '412', seq: 4, type: 'claimed' });
    await settle();
    expect(reads).toHaveLength(1);
    follower.stop();
  });

  it('看不懂的訊號整份重讀', async () => {
    const reads = fakeBackend();
    const { send, follower } = shellWith(board([card('412', 3)]));
    send('task', '{not json');
    await settle();
    expect(reads).toHaveLength(1);
    follower.stop();
  });

  it('步數跳號時換上卡片，也整份重讀', async () => {
    const reads = fakeBackend();
    const { send, tasks, follower } = shellWith(board([card('412', 3)]));
    send('task', upsertSignal(card('412', 5, { state: 'accepted' })));
    await settle();
    expect(tasks()[0]).toMatchObject({ state: 'accepted' });
    expect(reads).toHaveLength(1);
    follower.stop();
  });

  it('營業日是隔天的訊號整份重讀，卡片不換', async () => {
    const reads = fakeBackend();
    const { send, tasks, follower } = shellWith(board([card('412', 3)]));
    send('task', upsertSignal(card('412', 4, { state: 'accepted' }), 4, '2026-11-19'));
    await settle();
    expect(tasks()[0]).toMatchObject({ state: 'pool' });
    expect(reads).toHaveLength(1);
    follower.stop();
  });

  it('resync、recovered 整份重讀', async () => {
    const reads = fakeBackend();
    const { send, follower } = shellWith(board([]));
    send('resync');
    await settle();
    expect(reads).toHaveLength(1);
    follower.stop();
  });

  it('重讀還沒回來時收到較新的卡片，讀回來的舊看板不會把它蓋掉', async () => {
    const reads = fakeBackend();
    const { queryClient, send, tasks, follower } = shellWith(board([card('412', 3)]));
    send('task', { stream_pos: 1, task_id: '999', seq: 2, type: 'claimed' });
    await settle();
    send('task', upsertSignal(card('412', 4, { state: 'accepted' })));
    const readBefore = queryClient.getQueryState(deskBoardQuery.queryKey)!.dataUpdatedAt;
    reads[0]!(board([card('412', 3, { state: 'pool' })]));
    await vi.waitFor(() =>
      expect(queryClient.getQueryState(deskBoardQuery.queryKey)!.dataUpdatedAt).not.toBe(readBefore),
    );
    expect(queryClient.getQueryState(deskBoardQuery.queryKey)!.fetchStatus).toBe('idle');
    expect(tasks()[0]).toMatchObject({ state: 'accepted', seq: 4 });
    follower.stop();
  });

  it('開著的詳情是那張單時重讀詳情，別張單的訊號不重讀它', async () => {
    fakeBackend();
    const { queryClient, send, follower } = shellWith(board([card('412', 3), card('208', 3)]));
    const readDetail = vi.fn(() => Promise.resolve({}));
    new QueryObserver(queryClient, { queryKey: ['desk-task', '412'], queryFn: readDetail, staleTime: Infinity }).subscribe(
      () => {},
    );
    await vi.waitFor(() => expect(readDetail).toHaveBeenCalledTimes(1));
    send('task', upsertSignal(card('208', 4)));
    await settle();
    expect(readDetail).toHaveBeenCalledTimes(1);
    send('task', upsertSignal(card('412', 4)));
    await vi.waitFor(() => expect(readDetail).toHaveBeenCalledTimes(2));
    follower.stop();
  });
});

describe('followBoard：換日', () => {
  it('到了隔天 00:00 自己整份重讀；讀回來還是舊營業日就一分鐘後再讀', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] });
    vi.setSystemTime(new Date('2026-11-18T23:58:00+08:00'));
    // 櫃檯電腦的時鐘慢了 30 秒，等的是後端的 00:00。
    useServerClockStore.getState().syncWith('2026-11-18T23:58:30+08:00', new Date());
    const reads = fakeBackend();
    const { tasks, follower } = shellWith(
      board([card('301', 5, { state: 'done' }), card('412', 3)], '2026-11-18', '2026-11-18T23:58:30+08:00'),
    );

    await vi.advanceTimersByTimeAsync(89_000);
    expect(reads).toHaveLength(0);
    await vi.advanceTimersByTimeAsync(2_000);
    expect(reads).toHaveLength(1);

    reads[0]!(board([card('301', 5, { state: 'done' }), card('412', 3)], '2026-11-18', '2026-11-19T00:00:01+08:00'));
    await vi.advanceTimersByTimeAsync(59_000);
    expect(reads).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(2_000);
    expect(reads).toHaveLength(2);

    reads[1]!(board([card('412', 3)], '2026-11-19', '2026-11-19T00:01:00+08:00'));
    await vi.advanceTimersByTimeAsync(10 * 60_000);
    expect(reads).toHaveLength(2);
    expect(tasks().map((task) => task.id)).toEqual(['412']);
    follower.stop();
  });

  it('連線關掉後不再為換日重讀', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] });
    vi.setSystemTime(new Date('2026-11-18T23:58:00+08:00'));
    useServerClockStore.getState().syncWith('2026-11-18T23:58:00+08:00', new Date());
    const reads = fakeBackend();
    const { follower } = shellWith(board([], '2026-11-18', '2026-11-18T23:58:00+08:00'));
    follower.stop();
    await vi.advanceTimersByTimeAsync(5 * 60_000);
    expect(reads).toHaveLength(0);
  });
});
