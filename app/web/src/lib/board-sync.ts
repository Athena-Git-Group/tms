import type { DeskBoard, DeskTask } from '../api/types';
import { utcOffsetMinutes } from './server-clock';

/**
 * 看板快取怎麼跟著即時訊號帶來的卡片變，以及什麼時候要整份重讀。全是純函式，方便用單元測試驗。
 *
 * 每張卡片帶著它是這張單第幾步的樣子（`DeskTask.seq`）。畫面對一張單「記得的步數」是看板上那張卡片的步數，
 * 或訊號說它不在看板上時記下的步數，兩者取大。比記得的步數新的卡片才換上，一樣或較舊的不理，
 * 所以訊號重複、晚到、和整份重讀交錯，都不會把畫面倒回舊的樣子。
 */

/** 訊號帶來的一張卡片：`card` 是 `null` 表示後端說這張單現在不在看板上。 */
export interface CardPush {
  taskId: string;
  /** 觸發訊號的事件是第幾步，只拿來發現漏掉的訊號。 */
  seq: number;
  /** 卡片是第幾步的樣子，只拿來決定換不換。 */
  cardSeq: number;
  businessDate: string;
  card: DeskTask | null;
}

/** 換上過的一張卡片，`receivedOrder` 是收到的先後，和整份重讀開始的先後比。 */
export interface ReceivedPush {
  cardSeq: number;
  businessDate: string;
  card: DeskTask | null;
  receivedOrder: number;
}

/** 以單的 id 為鍵，記著換上過的卡片；看板上已經沒有的單也記著它是在第幾步拿掉的。 */
export type ReceivedPushes = ReadonlyMap<string, ReceivedPush>;

function knownSeq(board: DeskBoard, pushes: ReceivedPushes, taskId: string): number | undefined {
  const onBoard = board.tasks.find((task) => task.id === taskId)?.seq;
  const pushed = pushes.get(taskId)?.cardSeq;
  if (onBoard === undefined) return pushed;
  if (pushed === undefined) return onBoard;
  return Math.max(onBoard, pushed);
}

function withCard(board: DeskBoard, taskId: string, card: DeskTask | null): DeskBoard {
  const rest = board.tasks.filter((task) => task.id !== taskId);
  if (card === null) return rest.length === board.tasks.length ? board : { ...board, tasks: rest };
  if (rest.length === board.tasks.length) return { ...board, tasks: [...board.tasks, card] };
  return { ...board, tasks: board.tasks.map((task) => (task.id === taskId ? card : task)) };
}

/**
 * 收到一張卡片時看板變成什麼樣子，以及要不要另外整份重讀。
 *
 * 營業日和看板不同時不換：看板範圍跟著營業日變，這張卡片該不該在看板上要由整份重讀決定。
 * 訊號的事件步數比記得的步數多 2 以上，表示中間漏了訊號，別張單的訊號也可能漏了，換上之外還要整份重讀。
 * 畫面完全不認得的單（沒在看板上、也沒記過）沒有步數可比，直接照卡片放上或不理。
 */
export function applyCardPush(
  board: DeskBoard,
  pushes: ReceivedPushes,
  push: CardPush,
  receivedOrder: number,
): { board: DeskBoard; pushes: ReceivedPushes; needsFullRead: boolean } {
  if (push.businessDate !== board.businessDate) return { board, pushes, needsFullRead: true };
  const known = knownSeq(board, pushes, push.taskId);
  if (known !== undefined && push.cardSeq <= known) return { board, pushes, needsFullRead: false };
  const next = new Map(pushes);
  next.set(push.taskId, { cardSeq: push.cardSeq, businessDate: push.businessDate, card: push.card, receivedOrder });
  return {
    board: withCard(board, push.taskId, push.card),
    pushes: next,
    needsFullRead: known !== undefined && push.seq > known + 1,
  };
}

/**
 * 整份看板讀回來時要放進快取的樣子：讀取開始之後才收到、而且比讀回來的那張新的卡片，保留卡片那一份
 * （包含後端說已經不在看板上的）。讀取是在送出時的那一刻取資料，讀取期間換上的卡片可能比它新；
 * 直接用讀回來的那份，畫面會倒回舊的樣子，而那張單之後不一定還有訊號來糾正。
 * 營業日和讀回來的不同的卡片不保留，看板範圍以讀回來的為準。
 *
 * 「已經不在看板上」的記號留到換日為止。拿掉的單不在讀回來的看板上，看板沒有它的步數可比；
 * 少了這個記號，之後晚到的同一張單的舊卡片會被當成新單放回看板。
 * 其他卡片的步數已經寫在看板上的卡片裡，讀取開始前收到的就不再另外記。
 */
export function mergeFullRead(
  fresh: DeskBoard,
  pushes: ReceivedPushes,
  readStartedOrder: number,
): { board: DeskBoard; pushes: ReceivedPushes } {
  let board = fresh;
  const kept = new Map<string, ReceivedPush>();
  for (const [taskId, push] of pushes) {
    if (push.businessDate !== fresh.businessDate) continue;
    if (push.receivedOrder > readStartedOrder) {
      const freshSeq = fresh.tasks.find((task) => task.id === taskId)?.seq;
      if (freshSeq === undefined || freshSeq < push.cardSeq) board = withCard(board, taskId, push.card);
      kept.set(taskId, push);
    } else if (push.card === null) {
      kept.set(taskId, push);
    }
  }
  return { board, pushes: kept };
}

/** 讀回來的營業日還沒換時，隔多久再讀一次。 */
export const BUSINESS_DAY_RETRY_MS = 60_000;

/**
 * 從校正後的現在起，等多久要為了換日整份重讀一次看板。
 * 換日時刻是 `businessDate` 的隔天 00:00，時差照 `serverTime` 尾端的時差（後端伺服器時區，飯店沒有另外的換日時刻）。
 * 已經過了換日時刻、看板卻還是舊的營業日（例如兩邊時鐘差了幾秒），一分鐘後再讀，直到換過去。
 * `serverTime` 讀不出時差時回 `null`，不排換日重讀，靠訊號帶來的營業日發現換日。
 */
export function businessDayRereadDelayMs(board: Pick<DeskBoard, 'businessDate' | 'serverTime'>, nowMs: number): number | null {
  const offset = utcOffsetMinutes(board.serverTime);
  const date = /^(\d{4})-(\d{2})-(\d{2})$/.exec(board.businessDate);
  if (offset === null || !date) return null;
  const nextMidnight = Date.UTC(Number(date[1]), Number(date[2]) - 1, Number(date[3]) + 1) - offset * 60_000;
  const delay = nextMidnight - nowMs;
  return delay > 0 ? delay : BUSINESS_DAY_RETRY_MS;
}
