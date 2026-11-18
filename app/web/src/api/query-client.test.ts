import { describe, expect, it } from 'vitest';
import { UNTRUSTED_REFETCH_MS } from '../features/realtime/live-connection';
import { REREAD_INTERVAL_MS, retryAfterFailure } from './query-client';

describe('retryAfterFailure', () => {
  it('讀取失敗時每 4 秒再試，和即時訊號不可信時的定時重抓同一個間隔', () => {
    expect(REREAD_INTERVAL_MS).toBe(4_000);
    expect(retryAfterFailure({ state: { status: 'error' } })).toBe(REREAD_INTERVAL_MS);
    expect(UNTRUSTED_REFETCH_MS).toBe(REREAD_INTERVAL_MS);
  });

  it.each(['success', 'pending'])('%s 時不定時再試', (status) => {
    expect(retryAfterFailure({ state: { status } })).toBe(false);
  });
});
