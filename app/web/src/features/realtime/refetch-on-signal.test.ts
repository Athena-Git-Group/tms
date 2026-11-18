import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { deskBoardQuery } from '../../api/desk';
import type { DeskBoard } from '../../api/types';
import { refetchForSignal } from './refetch-on-signal';

function clientWithBoard(state: 'success' | 'error' | 'none') {
  const queryClient = new QueryClient();
  if (state === 'success') queryClient.setQueryData(deskBoardQuery.queryKey, { tasks: [] } as unknown as DeskBoard);
  if (state === 'error') {
    queryClient
      .getQueryCache()
      .build(queryClient, { queryKey: deskBoardQuery.queryKey })
      .setState({ status: 'error', error: new Error('read failed') });
  }
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue();
  return { queryClient, invalidate };
}

describe('refetchForSignal', () => {
  it('task：讓看板失效，正在讀的那一次取消重來', () => {
    const { queryClient, invalidate } = clientWithBoard('success');
    refetchForSignal('task', queryClient);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: deskBoardQuery.queryKey });
  });

  it('task：看板讀取失敗時也重抓', () => {
    const { queryClient, invalidate } = clientWithBoard('error');
    refetchForSignal('task', queryClient);
    expect(invalidate).toHaveBeenCalledTimes(1);
  });

  it('resync：看板讀成功時重抓，正在讀的那一次讓它讀完', () => {
    const { queryClient, invalidate } = clientWithBoard('success');
    refetchForSignal('resync', queryClient);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: deskBoardQuery.queryKey }, { cancelRefetch: false });
  });

  it('resync：看板讀取失敗或還沒讀過時不重抓', () => {
    for (const state of ['error', 'none'] as const) {
      const { queryClient, invalidate } = clientWithBoard(state);
      refetchForSignal('resync', queryClient);
      expect(invalidate).not.toHaveBeenCalled();
    }
  });

  it('recovered：和 resync 一樣，看板讀成功時重抓一次，讀取失敗時交給定時再試', () => {
    const ok = clientWithBoard('success');
    refetchForSignal('recovered', ok.queryClient);
    expect(ok.invalidate).toHaveBeenCalledWith({ queryKey: deskBoardQuery.queryKey }, { cancelRefetch: false });
    const failed = clientWithBoard('error');
    refetchForSignal('recovered', failed.queryClient);
    expect(failed.invalidate).not.toHaveBeenCalled();
  });

  it('其他訊號不重抓', () => {
    const { queryClient, invalidate } = clientWithBoard('success');
    for (const signal of ['init', 'heartbeat', 'degraded', 'message']) refetchForSignal(signal, queryClient);
    expect(invalidate).not.toHaveBeenCalled();
  });
});
