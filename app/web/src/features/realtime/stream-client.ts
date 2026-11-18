import { authHeaders, backendUrl } from '../../api/client';
import {
  INITIAL_LIVE_STATE,
  SILENCE_LIMIT_MS,
  retryDelayMs,
  transition,
  type LiveEvent,
  type LiveState,
} from './live-connection';

export interface StreamEvent {
  event: string;
  data: string;
}

/**
 * 從累積的文字裡切出已經收完整的訊號，還沒收完整的部分留在 `rest`，等下一塊資料接上再切。
 * 每則訊號以空行結束，每行是「欄位:值」。冒號後面可以有一個空白，也可以沒有，後端送的沒有。
 * 以冒號開頭的行是註解，直接略過。
 */
export function takeStreamEvents(buffer: string): { events: StreamEvent[]; rest: string } {
  // 結尾單獨的 \r 先不換：它可能是被切在兩塊資料之間的 \r\n，換成 \n 會和下一塊開頭的 \n 湊成假的空行。
  const text = buffer.replace(/\r\n|\r(?!$)/g, '\n');
  const events: StreamEvent[] = [];
  let start = 0;
  let end = text.indexOf('\n\n', start);
  while (end >= 0) {
    const parsed = parseBlock(text.slice(start, end));
    if (parsed) events.push(parsed);
    start = end + 2;
    end = text.indexOf('\n\n', start);
  }
  return { events, rest: text.slice(start) };
}

function parseBlock(block: string): StreamEvent | null {
  let event = 'message';
  const data: string[] = [];
  let hasField = false;
  for (const line of block.split('\n')) {
    if (line === '' || line.startsWith(':')) continue;
    const colon = line.indexOf(':');
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') {
      event = value;
      hasField = true;
    } else if (field === 'data') {
      data.push(value);
      hasField = true;
    }
  }
  return hasField ? { event, data: data.join('\n') } : null;
}

export interface StaffStreamHandlers {
  onEvent: (event: StreamEvent) => void;
  onState?: (state: LiveState) => void;
}

/**
 * 串流改用 fetch 讀取並自行解析，不用瀏覽器內建的 `EventSource`，因為它無法帶 `Authorization` 標頭。
 */
export function openStaffStream(handlers: StaffStreamHandlers): () => void {
  let stopped = false;
  let controller: AbortController | null = null;
  let retryTimer: ReturnType<typeof setTimeout> | null = null;
  let state = INITIAL_LIVE_STATE;

  const feed = (event: LiveEvent) => {
    if (stopped) return;
    const next = transition(state, event);
    if (next === state) return;
    const enteredDisconnected = state.link !== 'disconnected' && next.link === 'disconnected';
    state = next;
    handlers.onState?.(state);
    if (enteredDisconnected) {
      retryTimer = setTimeout(() => {
        feed({ type: 'retryDue' });
        void connect();
      }, retryDelayMs(state));
    }
  };

  const connect = async () => {
    const current = new AbortController();
    controller = current;
    const feedIfCurrent = (event: LiveEvent) => {
      if (controller === current) feed(event);
    };
    let silenceTimer: ReturnType<typeof setTimeout> | null = null;
    const restartSilenceTimer = () => {
      if (silenceTimer !== null) clearTimeout(silenceTimer);
      silenceTimer = setTimeout(() => {
        feedIfCurrent({ type: 'silence' });
        current.abort();
      }, SILENCE_LIMIT_MS);
    };
    restartSilenceTimer();
    try {
      const response = await fetch(backendUrl('/staff/stream'), {
        headers: { ...authHeaders(), Accept: 'text/event-stream' },
        cache: 'no-store',
        signal: current.signal,
      });
      if (response.ok && response.body) {
        feedIfCurrent({ type: 'connected' });
        const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
        let buffer = '';
        for (;;) {
          const { done, value } = await reader.read();
          if (done || stopped) break;
          restartSilenceTimer();
          const { events, rest } = takeStreamEvents(buffer + value);
          buffer = rest;
          for (const event of events) {
            if (stopped) break;
            handlers.onEvent(event);
            feedIfCurrent({ type: 'signal', signal: event });
          }
        }
      }
    } catch {
    }
    if (silenceTimer !== null) clearTimeout(silenceTimer);
    feedIfCurrent({ type: 'disconnected' });
  };

  handlers.onState?.(state);
  void connect();

  return () => {
    stopped = true;
    if (retryTimer !== null) clearTimeout(retryTimer);
    controller?.abort();
  };
}
