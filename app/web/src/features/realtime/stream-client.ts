import { authHeaders, backendUrl } from '../../api/client';
import {
  INITIAL_LIVE_STATE,
  SILENCE_LIMIT_MS,
  retryDelayMs,
  transition,
  type LiveEvent,
  type LiveState,
} from './live-connection';

/**
 * 員工事件串流的客戶端：用 `fetch` 讀 `GET /staff/stream`、把收到的文字切成一則一則訊號、斷線後自己重連。
 * 這一層只管連線、讀取、解析與計時器，不知道收到訊號之後畫面要重抓什麼；那是呼叫端（`use-live-updates.ts`）的事。
 * 連線走到哪一步、可不可信、重連等多久，由 `live-connection.ts` 的狀態機決定。
 *
 * 不用瀏覽器內建的 `EventSource`，因為它帶不了 `Authorization` 標頭。
 */

/** 一則訊號：名稱（`init`、`resync`、`task`、`heartbeat`、`degraded`、`recovered`）與 `data` 那一行的原文。 */
export interface StreamEvent {
  event: string;
  data: string;
}

/**
 * 從累積的文字裡切出已經收完整的訊號。每則訊號以空行結束，每行是「欄位:值」，冒號後面的一個空白可有可無
 * （後端送的沒有空白）。以冒號開頭的行是註解，略過。最後一段還沒收到空行的留在 `rest`，等下一塊資料接上。
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
  /** 每收完整一則訊號就呼叫一次。 */
  onEvent: (event: StreamEvent) => void;
  /** 連線狀態改變時呼叫，開始連的時候先報一次初始狀態；關掉之後不再呼叫。 */
  onState?: (state: LiveState) => void;
}

/**
 * 外框掛上時開始連，登出或換人時用回傳的函式關掉；關掉之後即使還有資料在路上，也不會再交給畫面。
 *
 * 這一層把發生的事當成事件餵給狀態機，什麼時候重連、等多久都照狀態機算出的新狀態：
 * 進入斷線就排重連計時，計時到了再連。
 *
 * 斷線有四種樣子，都當成斷線、等一下再重連：讀取丟出錯誤（後端消失、後端到期收掉連線）、讀取正常結束、回應不是成功，
 * 以及超過 `SILENCE_LIMIT_MS` 什麼都沒收到（這時由這一層主動關掉連線）。
 * 後端不在時開發伺服器的代理回 502，不會丟錯，所以要先看回應是不是成功。
 * 憑證被拒（401）也一樣只是等一下再重連；登入失效時要不要回登入頁，不在這一層決定。
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
    // 太久沒資料而被主動關掉的連線，讀取會晚一點才丟錯；只收目前這條連線的事件，舊連線的斷線不能算到新連線頭上。
    const feedIfCurrent = (event: LiveEvent) => {
      if (controller === current) feed(event);
    };
    // 從開始連就計時，每收到一塊資料重新起算；後端連回應都不給的時候也算太久沒資料。
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
      // 斷線、被關掉、太久沒資料被主動關掉都會走到這裡，一律照斷線交給狀態機。
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
