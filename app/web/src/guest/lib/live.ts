import { REREAD_INTERVAL_MS } from '../../api/query-client';
import { transition, type LiveEvent, type LiveState } from '../../features/realtime/live-connection';

/**
 * 住客的連線狀態照櫃檯共用的 `transition` 推，只多一條：住客的串流沒有 `recovered`，
 * 重新連上之後收到的 `init` 就是唯一的恢復訊號。
 */
export function guestTransition(state: LiveState, event: LiveEvent): LiveState {
  const next = transition(state, event);
  if (event.type === 'signal' && event.signal.event === 'init' && next.link === 'connected' && !next.trusted) {
    return { ...next, trusted: true };
  }
  return next;
}

/**
 * 需求清單要不要定時重讀：連著、收過訊號、沒被宣告延遲時靠即時訊號，不定時重讀；
 * 斷線、太久沒訊號、收到 `degraded`、重新連上還沒收到 `init` 都每 4 秒重讀一次。
 */
export function guestRereadMs(state: LiveState): number | false {
  const live = state.link === 'connected' && state.receivedSignal && state.trusted;
  return live ? false : REREAD_INTERVAL_MS;
}
