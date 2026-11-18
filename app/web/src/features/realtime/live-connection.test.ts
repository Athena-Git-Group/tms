import { describe, expect, it } from 'vitest';
import {
  FIRST_RETRY_MS,
  INITIAL_LIVE_STATE,
  LATER_RETRY_MS,
  UNTRUSTED_REFETCH_MS,
  boardPollMs,
  retryDelayMs,
  showsConnectionAlert,
  transition,
  type LiveEvent,
  type LiveLink,
  type LiveState,
} from './live-connection';

const signal = (event: string, data = '{}'): LiveEvent => ({ type: 'signal', signal: { event, data } });
const heartbeat = (degraded: unknown): LiveEvent => signal('heartbeat', JSON.stringify({ stream_pos: 0, degraded }));

const state = (link: LiveLink, trusted: boolean, overrides: Partial<LiveState> = {}): LiveState => ({
  link,
  trusted,
  failuresInRow: 0,
  receivedSignal: false,
  ...overrides,
});

const LINKS: readonly LiveLink[] = ['connecting', 'connected', 'disconnected', 'reconnecting'];

/** 只當作「收過訊號」、不影響可不可信的訊號與讀不懂的心跳。 */
const NEUTRAL_SIGNALS: Record<string, LiveEvent> = {
  init: signal('init'),
  resync: signal('resync'),
  task: signal('task'),
  沒帶degraded的心跳: signal('heartbeat', '{"stream_pos":0}'),
  degraded不是布林的心跳: heartbeat('yes'),
  不是JSON的心跳: signal('heartbeat', 'not json'),
};

/** 會改變可不可信的訊號，以及收到後應該是可信還是不可信。 */
const TRUST_SIGNALS: [string, LiveEvent, boolean][] = [
  ['degraded', signal('degraded', '{"reason":"kafka_consumer_lag"}'), false],
  ['degraded 為 true 的心跳', heartbeat(true), false],
  ['recovered', signal('recovered'), true],
  ['degraded 為 false 的心跳', heartbeat(false), true],
];

describe('transition：connected（後端回了成功的串流）', () => {
  it.each(['connecting', 'reconnecting'] as const)('%s → connected，可不可信與重連計數不變', (link) => {
    for (const trusted of [true, false]) {
      const before = state(link, trusted, { failuresInRow: 2 });
      expect(transition(before, { type: 'connected' })).toEqual({ ...before, link: 'connected' });
    }
  });

  it.each(['connected', 'disconnected'] as const)('%s 時沒有作用，交回同一個物件', (link) => {
    const before = state(link, false);
    expect(transition(before, { type: 'connected' })).toBe(before);
  });
});

describe('transition：disconnected 與 silence（斷線、太久沒資料）', () => {
  it.each(['connecting', 'connected', 'reconnecting'] as const)('%s 遇到 silence → 斷線、不可信', (link) => {
    for (const trusted of [true, false]) {
      for (const receivedSignal of [true, false]) {
        const next = transition(state(link, trusted, { receivedSignal }), { type: 'silence' });
        expect(next.link).toBe('disconnected');
        expect(next.trusted).toBe(false);
      }
    }
  });

  it.each(['connecting', 'reconnecting'] as const)('%s 時連不上（disconnected）→ 斷線、不可信', (link) => {
    for (const trusted of [true, false]) {
      const next = transition(state(link, trusted), { type: 'disconnected' });
      expect(next.link).toBe('disconnected');
      expect(next.trusted).toBe(false);
    }
  });

  it('連上了卻一則訊號都沒收到就斷：算連線失敗，不可信', () => {
    const next = transition(state('connected', true), { type: 'disconnected' });
    expect(next.link).toBe('disconnected');
    expect(next.trusted).toBe(false);
  });

  it('收過訊號的連線斷掉：先維持原本的可不可信，等重連的結果', () => {
    for (const trusted of [true, false]) {
      const next = transition(state('connected', trusted, { receivedSignal: true }), { type: 'disconnected' });
      expect(next.link).toBe('disconnected');
      expect(next.trusted).toBe(trusted);
    }
  });

  for (const type of ['disconnected', 'silence'] as const) {
    it(`已經斷線時 ${type} 沒有作用，交回同一個物件`, () => {
      const before = state('disconnected', false, { failuresInRow: 3 });
      expect(transition(before, { type })).toBe(before);
    });
  }

  it('這次連線收過訊號才斷：重連計數歸零', () => {
    const next = transition(state('connected', true, { failuresInRow: 4, receivedSignal: true }), { type: 'disconnected' });
    expect(next).toEqual(state('disconnected', true, { failuresInRow: 0, receivedSignal: false }));
  });

  it('什麼訊號都沒收到就斷：重連計數加一', () => {
    expect(transition(state('connecting', true), { type: 'disconnected' }).failuresInRow).toBe(1);
    expect(transition(state('reconnecting', false, { failuresInRow: 1 }), { type: 'silence' }).failuresInRow).toBe(2);
    expect(transition(state('connected', true, { failuresInRow: 2 }), { type: 'silence' }).failuresInRow).toBe(3);
  });
});

describe('transition：retryDue（重連計時到了）', () => {
  it('disconnected → reconnecting，可不可信不變', () => {
    for (const trusted of [true, false]) {
      const before = state('disconnected', trusted, { failuresInRow: 2 });
      expect(transition(before, { type: 'retryDue' })).toEqual({ ...before, link: 'reconnecting' });
    }
  });

  it.each(['connecting', 'connected', 'reconnecting'] as const)('%s 時沒有作用，交回同一個物件', (link) => {
    const before = state(link, true);
    expect(transition(before, { type: 'retryDue' })).toBe(before);
  });
});

describe('transition：signal（收到一則訊號）', () => {
  it.each(TRUST_SIGNALS)('連線在線時收到 %s', (_, event, trustedAfter) => {
    for (const trusted of [true, false]) {
      const next = transition(state('connected', trusted), event);
      expect(next).toEqual(state('connected', trustedAfter, { receivedSignal: true }));
    }
  });

  it.each(Object.entries(NEUTRAL_SIGNALS))('連線在線時收到 %s：可不可信不變，記下收過訊號', (_, event) => {
    for (const trusted of [true, false]) {
      expect(transition(state('connected', trusted), event)).toEqual(state('connected', trusted, { receivedSignal: true }));
    }
  });

  it('已經收過訊號、可不可信也沒變時，交回同一個物件', () => {
    const before = state('connected', true, { receivedSignal: true });
    expect(transition(before, signal('task'))).toBe(before);
    expect(transition(before, heartbeat(false))).toBe(before);
  });

  it.each(['connecting', 'disconnected', 'reconnecting'] as const)('%s 時收到訊號沒有作用，交回同一個物件', (link) => {
    for (const [, event] of TRUST_SIGNALS) {
      const before = state(link, link === 'connecting');
      expect(transition(before, event)).toBe(before);
    }
  });
});

describe('從狀態讀出的值', () => {
  it.each(LINKS)('%s：可信時不顯示提醒列、不定時重抓；不可信時顯示提醒列、每 4 秒重抓', (link) => {
    expect(showsConnectionAlert(state(link, true))).toBe(false);
    expect(boardPollMs(state(link, true))).toBeNull();
    expect(showsConnectionAlert(state(link, false))).toBe(true);
    expect(boardPollMs(state(link, false))).toBe(UNTRUSTED_REFETCH_MS);
    expect(UNTRUSTED_REFETCH_MS).toBe(4_000);
  });

  it('剛收過訊號或第一次連不上等 1 秒，之後每次等 5 秒', () => {
    expect(FIRST_RETRY_MS).toBe(1_000);
    expect(LATER_RETRY_MS).toBe(5_000);
    expect(retryDelayMs(state('disconnected', false, { failuresInRow: 0 }))).toBe(FIRST_RETRY_MS);
    expect(retryDelayMs(state('disconnected', false, { failuresInRow: 1 }))).toBe(FIRST_RETRY_MS);
    expect(retryDelayMs(state('disconnected', false, { failuresInRow: 2 }))).toBe(LATER_RETRY_MS);
    expect(retryDelayMs(state('disconnected', false, { failuresInRow: 7 }))).toBe(LATER_RETRY_MS);
  });
});

const run = (events: LiveEvent[], from: LiveState = INITIAL_LIVE_STATE) => events.reduce(transition, from);

describe('情境', () => {
  it('剛開頁、第一次還在連的時候算可信：不顯示提醒列、不定時重抓', () => {
    expect(INITIAL_LIVE_STATE).toEqual(state('connecting', true));
    expect(showsConnectionAlert(INITIAL_LIVE_STATE)).toBe(false);
    expect(boardPollMs(INITIAL_LIVE_STATE)).toBeNull();
    const connected = run([{ type: 'connected' }, signal('init'), signal('resync')]);
    expect(showsConnectionAlert(connected)).toBe(false);
  });

  it('第一次就連不上：變成不可信，1 秒後重連', () => {
    const failed = run([{ type: 'disconnected' }]);
    expect(showsConnectionAlert(failed)).toBe(true);
    expect(retryDelayMs(failed)).toBe(FIRST_RETRY_MS);
  });

  it('斷線後第一次重連就成功：從頭到尾不曾不可信，也不定時重抓', () => {
    const steps = [
      { type: 'connected' },
      signal('init'),
      { type: 'disconnected' },
      { type: 'retryDue' },
      { type: 'connected' },
      signal('init'),
      signal('resync'),
      heartbeat(false),
    ] satisfies LiveEvent[];
    const states: LiveState[] = [INITIAL_LIVE_STATE];
    for (const event of steps) states.push(transition(states.at(-1) ?? INITIAL_LIVE_STATE, event));
    expect(states.map((s) => s.trusted)).toEqual(states.map(() => true));
    expect(states.some(showsConnectionAlert)).toBe(false);
    expect(states.every((s) => boardPollMs(s) === null)).toBe(true);
    expect(states.at(-1)?.link).toBe('connected');
  });

  it('斷線後重連失敗一次才變不可信', () => {
    const dropped = run([{ type: 'connected' }, signal('init'), { type: 'disconnected' }]);
    expect(showsConnectionAlert(dropped)).toBe(false);
    const retrying = transition(dropped, { type: 'retryDue' });
    expect(showsConnectionAlert(retrying)).toBe(false);
    const failed = transition(retrying, { type: 'disconnected' });
    expect(showsConnectionAlert(failed)).toBe(true);
    expect(boardPollMs(failed)).toBe(UNTRUSTED_REFETCH_MS);
    expect(retryDelayMs(failed)).toBe(FIRST_RETRY_MS);
  });

  it('重連失敗之後，單純重新連上不會回到可信，要等 recovered 或 degraded 為 false 的心跳', () => {
    const reconnected = run([
      { type: 'connected' },
      signal('init'),
      { type: 'disconnected' },
      { type: 'retryDue' },
      { type: 'disconnected' },
      { type: 'retryDue' },
      { type: 'connected' },
      signal('init'),
      signal('resync'),
      signal('task'),
    ]);
    expect(reconnected.link).toBe('connected');
    expect(showsConnectionAlert(reconnected)).toBe(true);
    expect(boardPollMs(reconnected)).toBe(UNTRUSTED_REFETCH_MS);

    expect(showsConnectionAlert(transition(reconnected, heartbeat(false)))).toBe(false);
    expect(boardPollMs(transition(reconnected, signal('recovered')))).toBeNull();
  });

  it('連線在線時太久沒資料：馬上不可信，不等重連的結果', () => {
    const silent = run([{ type: 'connected' }, signal('init'), { type: 'silence' }]);
    expect(showsConnectionAlert(silent)).toBe(true);
  });

  it('一直連不上：第一次 1 秒後重連，之後每 5 秒', () => {
    const attempt = [{ type: 'retryDue' }, { type: 'disconnected' }] satisfies LiveEvent[];
    const first = run([{ type: 'disconnected' }]);
    const second = run(attempt, first);
    const third = run(attempt, second);
    expect([first, second, third].map(retryDelayMs)).toEqual([FIRST_RETRY_MS, LATER_RETRY_MS, LATER_RETRY_MS]);
  });

  it('連上後收過訊號再斷：下一次一樣 1 秒後重連', () => {
    const flaky = run([{ type: 'disconnected' }, { type: 'retryDue' }, { type: 'disconnected' }]);
    expect(retryDelayMs(flaky)).toBe(LATER_RETRY_MS);
    const dropped = run([{ type: 'retryDue' }, { type: 'connected' }, signal('init'), { type: 'silence' }], flaky);
    expect(retryDelayMs(dropped)).toBe(FIRST_RETRY_MS);
  });

  it('連線在線時後端宣告降級：不可信，連線仍在線', () => {
    const degraded = run([{ type: 'connected' }, signal('degraded')]);
    expect(degraded.link).toBe('connected');
    expect(showsConnectionAlert(degraded)).toBe(true);
  });
});
