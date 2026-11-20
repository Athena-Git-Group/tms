import { describe, expect, it } from 'vitest';
import { ApiError, NetworkError } from '../../api/errors';
import { ENTRY_LOADING, entryReducer, scanErrorEvent, type EntryEvent, type EntryState } from './entry';

describe('entryReducer', () => {
  const LOADING = ENTRY_LOADING;
  const READY: EntryState = { kind: 'ready' };
  const UNAVAILABLE: EntryState = { kind: 'unavailable' };
  const RETRY: EntryState = { kind: 'retry' };
  const RETRYING: EntryState = { kind: 'retrying' };

  it.each<[string, EntryState, EntryEvent, EntryState]>([
    ['換到 session 就往下畫', LOADING, { type: 'scan-succeeded' }, READY],
    ['後端說這間房不能用就是無法使用頁', LOADING, { type: 'scan-room-vacant' }, UNAVAILABLE],
    ['限流、連不上、後端出錯是重試狀態', LOADING, { type: 'scan-failed' }, RETRY],
    ['正在用時重換成功，畫面不動', READY, { type: 'scan-succeeded' }, READY],
    ['正在用時重換遇到無法使用', READY, { type: 'scan-room-vacant' }, UNAVAILABLE],
    ['正在用時重換也失敗', READY, { type: 'scan-failed' }, RETRY],
    ['按了重試就等後端', RETRY, { type: 'retry-pressed' }, RETRYING],
    ['重試換到 session 就畫原本那一頁', RETRYING, { type: 'scan-succeeded' }, READY],
    ['重試時後端說這間房不能用', RETRYING, { type: 'scan-room-vacant' }, UNAVAILABLE],
    ['重試又失敗就留在重試狀態', RETRYING, { type: 'scan-failed' }, RETRY],
    ['還在等時再按重試不重送', RETRYING, { type: 'retry-pressed' }, RETRYING],
    ['沒停在重試狀態時按重試不算數', READY, { type: 'retry-pressed' }, READY],
    ['無法使用頁不會再變', UNAVAILABLE, { type: 'scan-succeeded' }, UNAVAILABLE],
  ])('%s', (_name, state, event, next) => {
    expect(entryReducer(state, event)).toEqual(next);
  });

  it('五個狀態都走得到', () => {
    const seen = new Set<string>([LOADING.kind]);
    const events: EntryEvent[] = [
      { type: 'scan-succeeded' },
      { type: 'scan-room-vacant' },
      { type: 'scan-failed' },
      { type: 'retry-pressed' },
    ];
    let frontier: EntryState[] = [LOADING];
    while (frontier.length > 0) {
      frontier = frontier
        .flatMap((state) => events.map((event) => entryReducer(state, event)))
        .filter((state) => !seen.has(state.kind) && seen.add(state.kind));
    }
    expect([...seen].sort()).toEqual(['loading', 'ready', 'retry', 'retrying', 'unavailable']);
  });
});

describe('scanErrorEvent', () => {
  it('409 是這間房不能用', () => {
    expect(scanErrorEvent(new ApiError(409, { code: 'room_vacant', message: '' }))).toEqual({
      type: 'scan-room-vacant',
    });
  });

  it.each([
    ['限流', new ApiError(429, { code: 'rate_limited', message: '' })],
    ['後端出錯', new ApiError(500, { code: 'internal_error', message: '' })],
    ['連不上', new NetworkError(new TypeError('Failed to fetch'))],
  ])('%s不算這間房不能用', (_name, error) => {
    expect(scanErrorEvent(error)).toEqual({ type: 'scan-failed' });
  });
});
