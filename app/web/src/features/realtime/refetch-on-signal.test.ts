import { QueryClient, QueryObserver } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cancelBoardReadForDispatch, deskBoardQuery } from '../../api/desk';
import type { DeskBoard } from '../../api/types';
import { createBoardRereader } from './refetch-on-signal';

interface BoardRead {
  signal: AbortSignal;
  respond: () => void;
}

function fakeBackend() {
  const reads: BoardRead[] = [];
  const log: string[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(
      (_url: string, init: RequestInit) =>
        new Promise<Response>((resolve, reject) => {
          const signal = init.signal!;
          log.push(`read ${reads.length + 1} started`);
          signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
          reads.push({
            signal,
            respond: () => resolve(new Response(JSON.stringify({ serverTime: new Date().toISOString(), tasks: [] }))),
          });
        }),
    ),
  );
  return { reads, log };
}

function shellWithBoard(state: 'success' | 'error') {
  const queryClient = new QueryClient();
  if (state === 'success') queryClient.setQueryData(deskBoardQuery.queryKey, { tasks: [] } as unknown as DeskBoard);
  else {
    queryClient
      .getQueryCache()
      .build(queryClient, { queryKey: deskBoardQuery.queryKey })
      .setState({ status: 'error', error: new Error('read failed') });
  }
  const observer = new QueryObserver(queryClient, { ...deskBoardQuery, staleTime: Infinity, retryOnMount: false });
  const unsubscribe = observer.subscribe(() => {});
  return { queryClient, unsubscribe, reread: createBoardRereader(queryClient) };
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('createBoardRereader', () => {
  it('stale：看板沒在讀時馬上讀一次', async () => {
    const { reads } = fakeBackend();
    const { reread } = shellWithBoard('success');
    reread('stale');
    await settle();
    expect(reads).toHaveLength(1);
  });

  it('stale：正在讀時連來 20 個訊號，讀完只再讀一次，總共兩次', async () => {
    const { reads } = fakeBackend();
    const { reread } = shellWithBoard('success');
    for (let i = 0; i < 20; i += 1) reread('stale');
    await settle();
    expect(reads).toHaveLength(1);

    reads[0]!.respond();
    await vi.waitFor(() => expect(reads).toHaveLength(2));
    expect(reads[0]!.signal.aborted).toBe(false);

    reads[1]!.respond();
    await settle();
    await settle();
    expect(reads).toHaveLength(2);
  });

  it('stale：別處已開始的讀取之後才來的訊號，讀完後一定再讀一次，而且同時只有一次在讀', async () => {
    const { reads, log } = fakeBackend();
    const { queryClient, reread } = shellWithBoard('success');
    void queryClient.refetchQueries({ queryKey: deskBoardQuery.queryKey });
    await settle();
    log.push('task signal');
    reread('stale');
    await settle();
    expect(reads).toHaveLength(1);

    reads[0]!.respond();
    await vi.waitFor(() => expect(reads).toHaveLength(2));
    expect(log).toEqual(['read 1 started', 'task signal', 'read 2 started']);
  });

  it('stale：讀到一半又變了，再讀的那一次期間再來訊號，還會再讀一次', async () => {
    const { reads } = fakeBackend();
    const { reread } = shellWithBoard('success');
    reread('stale');
    reread('stale');
    await settle();
    reads[0]!.respond();
    await vi.waitFor(() => expect(reads).toHaveLength(2));
    reread('stale');
    reads[1]!.respond();
    await vi.waitFor(() => expect(reads).toHaveLength(3));
    reads[2]!.respond();
    await settle();
    await settle();
    expect(reads).toHaveLength(3);
  });

  it('stale：讀到一半又變了，但那一次被派單取消時不補讀，留給派單完成後的重讀', async () => {
    const { reads } = fakeBackend();
    const { queryClient, reread } = shellWithBoard('success');
    reread('stale');
    reread('stale');
    await settle();
    await cancelBoardReadForDispatch(queryClient);
    await settle();
    await settle();
    expect(reads).toHaveLength(1);
    expect(reads[0]!.signal.aborted).toBe(true);
    reread('stale');
    await settle();
    expect(reads).toHaveLength(2);
  });

  it('stale：讀到一半又變了，先換上送來的卡片、再被派單取消時，也不補讀', async () => {
    const { reads } = fakeBackend();
    const { queryClient, reread } = shellWithBoard('success');
    reread('stale');
    reread('stale');
    await settle();
    queryClient.setQueryData(deskBoardQuery.queryKey, { tasks: [{ id: 'pushed' }] } as unknown as DeskBoard);
    await cancelBoardReadForDispatch(queryClient);
    queryClient.setQueryData(deskBoardQuery.queryKey, { tasks: [{ id: 'assumed-assigned' }] } as unknown as DeskBoard);
    await settle();
    await settle();
    expect(reads).toHaveLength(1);
    expect(reads[0]!.signal.aborted).toBe(true);
  });

  it('stale：讀到一半又變了，那一次被更新的讀取接手時，接手的讀完後還會再讀一次', async () => {
    const { reads } = fakeBackend();
    const { queryClient, reread } = shellWithBoard('success');
    reread('stale');
    reread('stale');
    await settle();
    void queryClient.refetchQueries({ queryKey: deskBoardQuery.queryKey });
    await settle();
    expect(reads).toHaveLength(2);
    expect(reads[0]!.signal.aborted).toBe(true);
    reads[1]!.respond();
    await vi.waitFor(() => expect(reads).toHaveLength(3));
  });

  it('stale：看板讀取失敗時也重抓', async () => {
    const { reads } = fakeBackend();
    const { reread } = shellWithBoard('error');
    reread('stale');
    await settle();
    expect(reads).toHaveLength(1);
  });

  it('reconnected：看板讀成功時重抓一次', async () => {
    const { reads } = fakeBackend();
    const { reread } = shellWithBoard('success');
    reread('reconnected');
    await settle();
    expect(reads).toHaveLength(1);
  });

  it('reconnected：正在讀時不取消那一次，也不另外再讀', async () => {
    const { reads } = fakeBackend();
    const { queryClient, reread } = shellWithBoard('success');
    void queryClient.refetchQueries({ queryKey: deskBoardQuery.queryKey });
    await settle();
    reread('reconnected');
    reread('reconnected');
    await settle();
    expect(reads).toHaveLength(1);
    expect(reads[0]!.signal.aborted).toBe(false);

    reads[0]!.respond();
    await settle();
    await settle();
    expect(reads).toHaveLength(1);
    expect(queryClient.getQueryState(deskBoardQuery.queryKey)?.status).toBe('success');
  });

  it('reconnected：看板讀取失敗時不重抓，交給外框的定時再試', async () => {
    const { reads } = fakeBackend();
    const { reread } = shellWithBoard('error');
    reread('reconnected');
    reread('reconnected');
    await settle();
    expect(reads).toHaveLength(0);
  });
});

describe('看板讀取被取消', () => {
  it('送出後端寫入後重讀時，舊的那一次真的在網路上取消，看板不會變成讀取失敗', async () => {
    const { reads } = fakeBackend();
    const { queryClient, reread } = shellWithBoard('success');
    reread('stale');
    await settle();
    void queryClient.invalidateQueries({ queryKey: deskBoardQuery.queryKey });
    await settle();
    expect(reads[0]!.signal.aborted).toBe(true);
    expect(queryClient.getQueryState(deskBoardQuery.queryKey)?.status).toBe('success');

    reads[1]!.respond();
    await settle();
    expect(queryClient.getQueryState(deskBoardQuery.queryKey)?.status).toBe('success');
  });

  it('沒有畫面看著看板時，正在讀的那一次取消，看板留在上一次讀成功的樣子', async () => {
    const { reads } = fakeBackend();
    const { queryClient, unsubscribe, reread } = shellWithBoard('success');
    reread('stale');
    await settle();
    unsubscribe();
    await settle();
    expect(reads[0]!.signal.aborted).toBe(true);
    expect(queryClient.getQueryState(deskBoardQuery.queryKey)).toMatchObject({ status: 'success', fetchStatus: 'idle' });
  });
});
