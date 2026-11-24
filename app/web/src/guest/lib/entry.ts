import { ApiError } from '../../api/errors';

/**
 * 進入層這一刻要畫什麼：換 session 中、換到了就往下畫、這間房不能用、連不上要住客自己再試、按了重試還在等。
 * 「原本要去的那一頁」就是目前的網址，換 session 的過程網址不變，這裡不另外記。
 */
export type EntryState =
  | { kind: 'loading' }
  | { kind: 'ready' }
  | { kind: 'unavailable' }
  | { kind: 'retry' }
  | { kind: 'retrying' };

/** 換 session 的結果，加上住客按「重試」；被拒絕與連不上是兩件事，畫面不同。 */
export type EntryEvent =
  | { type: 'scan-succeeded' }
  | { type: 'scan-room-vacant' }
  | { type: 'scan-failed' }
  | { type: 'retry-pressed' };

export const ENTRY_LOADING: EntryState = { kind: 'loading' };

export function entryReducer(state: EntryState, event: EntryEvent): EntryState {
  if (state.kind === 'unavailable') return state;
  switch (event.type) {
    case 'scan-succeeded':
      return state.kind === 'retry' ? state : { kind: 'ready' };
    case 'scan-room-vacant':
      return { kind: 'unavailable' };
    case 'scan-failed':
      return { kind: 'retry' };
    case 'retry-pressed':
      return state.kind === 'retry' ? { kind: 'retrying' } : state;
  }
}

/** 空房、代碼作廢、代碼不存在，後端都回同一個 409；其他失敗（限流、連不上、後端出錯）不算這間房不能用。 */
export function scanErrorEvent(error: unknown): EntryEvent {
  return error instanceof ApiError && error.status === 409 ? { type: 'scan-room-vacant' } : { type: 'scan-failed' };
}
