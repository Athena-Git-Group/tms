import { REREAD_INTERVAL_MS } from '../../api/query-client';
import type { LiveState } from '../../features/realtime/live-connection';

export function guestRereadMs(state: LiveState): number | false {
  const live = state.link === 'connected' && state.receivedSignal && state.trusted;
  return live ? false : REREAD_INTERVAL_MS;
}
