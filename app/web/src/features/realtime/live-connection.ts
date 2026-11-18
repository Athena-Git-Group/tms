/**
 * 即時連線的狀態機。連線走到哪一步、即時訊號可不可信、下一次重連要等多久，都只由這裡的 `transition` 算出來；
 * 提醒列、看板的定時重抓、重連計時器都從狀態讀值，不自己另外記。
 *
 * 這一層是純函式，不碰 `fetch`、計時器與 React。實際去連、讀、計時的是 `stream-client.ts`，
 * 它把發生的事當成事件餵進 `transition`，再照新狀態決定要不要排重連。
 *
 * 可不可信照契約 `staffStream` 的櫃檯用法：
 * - 變成不可信：連線失敗（重連或第一次連沒連上）、太久沒資料、收到 `degraded`、收到 `degraded` 為 true 的 `heartbeat`。
 * - 用得好好的連線斷掉不算：先維持可信，馬上重連一次。第一次重連就連上時，提醒列從頭到尾都不出現；
 *   斷線這段期間漏掉的變化，靠後端重連時送來的 `resync` 補讀，所以這段時間也不定時重抓。
 *   重連沒連上，才變成不可信。
 * - 回到可信：連線在線，而且收到 `recovered` 或 `degraded` 為 false 的 `heartbeat`。
 *   變成不可信之後，只是重新連上還不算，因為重連的那一刻後端可能仍在降級。
 * - 剛開頁、第一次還在連的時候算可信，不把每次開頁都當成連線異常；第一次就連不上才變成不可信。
 */

import { REREAD_INTERVAL_MS } from '../../api/query-client';
import type { StreamEvent } from './stream-client';

/** 斷線後第一次重連等這麼久。後端每 30 分鐘會主動收掉連線一次，這種情況 1 秒內就連得回去。 */
export const FIRST_RETRY_MS = 1_000;

/** 連續連不上時，之後每次重連的間隔。後端沒開、或憑證被拒時不要一直打。 */
export const LATER_RETRY_MS = 5_000;

/**
 * 連線開著卻這麼久什麼都沒收到，就當成斷線。
 * 後端每 15 秒送一次心跳，約兩個心跳週期都沒到，代表後端卡住或正在關機，連線本身不會報錯。
 */
export const SILENCE_LIMIT_MS = 35_000;

/** 即時訊號不可信時定時重抓看板的間隔，和讀取失敗時的再試是同一個間隔。 */
export const UNTRUSTED_REFETCH_MS = REREAD_INTERVAL_MS;

/**
 * 連線走到哪一步：
 * - `connecting`：第一次連，還沒有回應。
 * - `connected`：後端回了成功的串流，正在讀。
 * - `disconnected`：斷了（或連不上），正在等下一次重連。
 * - `reconnecting`：斷線後再連，還沒有回應。
 */
export type LiveLink = 'connecting' | 'connected' | 'disconnected' | 'reconnecting';

export interface LiveState {
  link: LiveLink;
  /** 即時訊號可不可信；不可信時顯示提醒列並定時重抓看板。 */
  trusted: boolean;
  /** 連續幾次連上後什麼訊號都沒收到就斷，決定下一次重連等多久。 */
  failuresInRow: number;
  /** 這一次連線收過至少一則訊號。斷線時用來分辨「一般的斷線」和「一直連不上」。 */
  receivedSignal: boolean;
}

/**
 * 餵進狀態機的事件：
 * - `connected`：後端回了成功的串流。
 * - `disconnected`：讀取丟錯、讀取正常結束、或回應不是成功。
 * - `silence`：連線開著卻超過 `SILENCE_LIMIT_MS` 沒有資料；之後連線會被關掉，照斷線處理。
 * - `retryDue`：重連計時到了，開始再連。
 * - `signal`：收完整一則訊號。
 */
export type LiveEvent =
  | { type: 'connected' }
  | { type: 'disconnected' }
  | { type: 'silence' }
  | { type: 'retryDue' }
  | { type: 'signal'; signal: StreamEvent };

/** 剛開頁、第一次還在連。 */
export const INITIAL_LIVE_STATE: LiveState = {
  link: 'connecting',
  trusted: true,
  failuresInRow: 0,
  receivedSignal: false,
};

/**
 * 由目前狀態與事件算出下一個狀態。
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
        // 太久沒資料一定不可信；斷線則只有這次連線失敗才不可信，用得好好的連線斷掉先維持原狀。
        trusted: event.type === 'disconnected' && !attemptFailed(state) && state.trusted,
        // 這次連上後有收到訊號，代表只是一般的斷線，很快就連得回去；一直連不上才拉長間隔。
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

/**
 * 這次連線算不算失敗：還沒等到後端回應就斷，或連上了卻一則訊號都沒收到就斷。
 * 後端連上就會先送 `init`，什麼都沒收到代表這條連線其實沒有用過。
 */
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

/** 心跳帶的 `degraded`；內容讀不懂或沒帶時回 null，維持原狀。 */
function heartbeatDegraded(data: string): boolean | null {
  try {
    const parsed: unknown = JSON.parse(data);
    if (parsed && typeof parsed === 'object' && 'degraded' in parsed && typeof parsed.degraded === 'boolean') {
      return parsed.degraded;
    }
  } catch {
    // 讀不懂的心跳不改變現況。
  }
  return null;
}

/** 要不要顯示「即時連線異常」提醒列。這裡只看即時連線；看板已有資料後重讀失敗的情形由外框另外判斷。 */
export function showsConnectionAlert(state: LiveState): boolean {
  return !state.trusted;
}

/** 看板要每隔多久定時重抓；可信時回 null，只靠即時訊號更新。 */
export function boardPollMs(state: LiveState): number | null {
  return state.trusted ? null : UNTRUSTED_REFETCH_MS;
}

/** 斷線後等多久再連：剛收過訊號或第一次連不上等 1 秒，之後每次等 5 秒。 */
export function retryDelayMs(state: LiveState): number {
  return state.failuresInRow <= 1 ? FIRST_RETRY_MS : LATER_RETRY_MS;
}
