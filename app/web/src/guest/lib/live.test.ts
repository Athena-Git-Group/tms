import { describe, expect, it } from 'vitest';
import { INITIAL_LIVE_STATE, type LiveEvent, type LiveState } from '../../features/realtime/live-connection';
import { guestRereadMs, guestTransition } from './live';

const signal = (event: string, data = '{}'): LiveEvent => ({ type: 'signal', signal: { event, data } });

function run(...events: LiveEvent[]): LiveState {
  return events.reduce(guestTransition, INITIAL_LIVE_STATE);
}

const CONNECTED = [{ type: 'connected' }, signal('init'), signal('resync')] as const satisfies LiveEvent[];

describe('guest live connection', () => {
  it('follows the stream without periodic re-reads once connected and init arrived', () => {
    expect(guestRereadMs(run(...CONNECTED))).toBe(false);
  });

  it('re-reads every 4 s while connected but before the first signal', () => {
    expect(guestRereadMs(run({ type: 'connected' }))).toBe(4_000);
  });

  it('re-reads every 4 s after the stream drops, after silence, and after degraded', () => {
    expect(guestRereadMs(run(...CONNECTED, { type: 'disconnected' }))).toBe(4_000);
    expect(guestRereadMs(run(...CONNECTED, { type: 'silence' }))).toBe(4_000);
    expect(guestRereadMs(run(...CONNECTED, signal('degraded')))).toBe(4_000);
  });

  it('stays on periodic re-reads while degraded, since the guest stream never sends recovered', () => {
    expect(guestRereadMs(run(...CONNECTED, signal('degraded'), signal('heartbeat', '{"stream_pos":3}')))).toBe(4_000);
  });

  it('goes back to live only after reconnecting and receiving init: degraded, drop, reconnect, init', () => {
    const dropped = run(...CONNECTED, signal('degraded'), { type: 'disconnected' }, { type: 'retryDue' });
    expect(guestRereadMs(dropped)).toBe(4_000);
    const reconnected = guestTransition(dropped, { type: 'connected' });
    expect(guestRereadMs(reconnected)).toBe(4_000);
    expect(guestRereadMs(guestTransition(reconnected, signal('init')))).toBe(false);
  });

  it('goes back to live after failed reconnect attempts once a later attempt receives init', () => {
    const failing = run(
      ...CONNECTED,
      { type: 'disconnected' },
      { type: 'retryDue' },
      { type: 'disconnected' },
      { type: 'retryDue' },
    );
    expect(failing.trusted).toBe(false);
    expect(guestRereadMs(failing)).toBe(4_000);
    const back = [{ type: 'connected' } as const, signal('init')].reduce(guestTransition, failing);
    expect(guestRereadMs(back)).toBe(false);
  });

  it('does not treat init as recovery before the stream is connected', () => {
    const dropped = run(...CONNECTED, signal('degraded'), { type: 'disconnected' });
    expect(guestTransition(dropped, signal('init'))).toBe(dropped);
  });
});
