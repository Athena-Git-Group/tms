import { ApiError } from '../../api/errors';

export type EntryState =
  | { kind: 'loading' }
  | { kind: 'ready' }
  | { kind: 'unavailable' }
  | { kind: 'retry' }
  | { kind: 'retrying' };

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

export function scanErrorEvent(error: unknown): EntryEvent {
  return error instanceof ApiError && error.status === 409 ? { type: 'scan-room-vacant' } : { type: 'scan-failed' };
}
