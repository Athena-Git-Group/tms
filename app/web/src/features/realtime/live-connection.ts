import { REREAD_INTERVAL_MS } from '../../api/query-client';
import type { StreamEvent } from './stream-client';

export const FIRST_RETRY_MS = 1_000;

export const LATER_RETRY_MS = 5_000;

export const SILENCE_LIMIT_MS = 35_000;

export const UNTRUSTED_REFETCH_MS = REREAD_INTERVAL_MS;

export type LiveLink = 'connecting' | 'connected' | 'disconnected' | 'reconnecting';

export interface LiveState {
  link: LiveLink;
  trusted: boolean;
  failuresInRow: number;
  receivedSignal: boolean;
}

export type LiveEvent =
  | { type: 'connected' }
  | { type: 'disconnected' }
  | { type: 'silence' }
  | { type: 'retryDue' }
  | { type: 'signal'; signal: StreamEvent };

export const INITIAL_LIVE_STATE: LiveState = {
  link: 'connecting',
  trusted: true,
  failuresInRow: 0,
  receivedSignal: false,
};

/**
 * 事件在這個狀態下沒有作用時（例如還沒連上就收到訊號、已經斷了又報一次斷線），原樣交回同一個物件。
 * 外框把狀態放在 React 的 state 裡，同一個物件不會觸發重畫，所以每 15 秒一次的心跳不會讓整個外框重畫。
 */
export function transition(state: LiveState, event: LiveEvent): LiveState {
  switch (event.type) {
    case 'connected':
      return isAttempting(state.link) ? { ...state, link: 'connected' } : state;
    case 'disconnected':
    case 'silence':
      if (state.link === 'disconnected') return state;
      return {
        link: 'disconnected',
        trusted: event.type === 'disconnected' && !attemptFailed(state) && state.trusted,
        failuresInRow: attemptFailed(state) ? state.failuresInRow + 1 : 0,
        receivedSignal: false,
      };
    case 'retryDue':
      return state.link === 'disconnected' ? { ...state, link: 'reconnecting', receivedSignal: false } : state;
    case 'signal':
      return state.link === 'connected' ? afterSignal(state, event.signal) : state;
  }
}

function isAttempting(link: LiveLink): boolean {
  return link === 'connecting' || link === 'reconnecting';
}

function attemptFailed(state: LiveState): boolean {
  return isAttempting(state.link) || !state.receivedSignal;
}

function afterSignal(state: LiveState, signal: StreamEvent): LiveState {
  const trusted = trustedAfter(state.trusted, signal);
  if (trusted === state.trusted && state.receivedSignal) return state;
  return { ...state, trusted, receivedSignal: true };
}

function trustedAfter(trusted: boolean, { event, data }: StreamEvent): boolean {
  if (event === 'degraded') return false;
  if (event === 'recovered') return true;
  if (event === 'heartbeat') {
    const degraded = heartbeatDegraded(data);
    return degraded === null ? trusted : !degraded;
  }
  return trusted;
}

function heartbeatDegraded(data: string): boolean | null {
  try {
    const parsed: unknown = JSON.parse(data);
    if (parsed && typeof parsed === 'object' && 'degraded' in parsed && typeof parsed.degraded === 'boolean') {
      return parsed.degraded;
    }
  } catch {
  }
  return null;
}

export function showsConnectionAlert(state: LiveState): boolean {
  return !state.trusted;
}

export function boardPollMs(state: LiveState): number | null {
  return state.trusted ? null : UNTRUSTED_REFETCH_MS;
}

export function retryDelayMs(state: LiveState): number {
  return state.failuresInRow <= 1 ? FIRST_RETRY_MS : LATER_RETRY_MS;
}
