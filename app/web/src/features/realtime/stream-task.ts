import type { DeskTask } from '../../api/types';
import type { CardPush } from '../../lib/board-sync';

/**
 * `task` 訊號 `data` 那一行讀出來的樣子。
 * - `card`：帶著這張單在看板上現在的樣子（或「不在看板上」），畫面照 `applyCardPush` 的規則換那一張。
 * - `noCard`：沒帶卡片、內容看不懂，或卡片和步數對不起來。畫面不知道看板該變成什麼樣子，只能整份重讀；
 *   讀得出是哪一張單時帶著 `taskId`，開著的詳情面板照樣重讀那一張。
 */
export type StreamTask = { kind: 'card'; push: CardPush } | { kind: 'noCard'; taskId: string | null };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isStep(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0;
}

/**
 * 只在這裡檢查後端送來的內容，之後的程式都信任 `CardPush` 的型別。
 * 卡片本身照 `getDeskBoard` 的形狀直接用，只核對它是不是這張單、是不是說好的那一步：
 * 對不起來時換上它會讓畫面記錯步數，之後的訊號都比錯，所以寧可整份重讀。
 */
export function parseStreamTask(data: string): StreamTask {
  let payload: unknown;
  try {
    payload = JSON.parse(data);
  } catch {
    return { kind: 'noCard', taskId: null };
  }
  if (!isRecord(payload)) return { kind: 'noCard', taskId: null };
  const taskId = typeof payload.task_id === 'string' ? payload.task_id : null;
  const { seq, card_seq: cardSeq, business_date: businessDate, board, card } = payload;
  if (taskId === null || !isStep(seq) || !isStep(cardSeq) || typeof businessDate !== 'string') {
    return { kind: 'noCard', taskId };
  }
  if (board === 'remove') return { kind: 'card', push: { taskId, seq, cardSeq, businessDate, card: null } };
  if (board === 'upsert' && isRecord(card) && card.id === taskId && card.seq === cardSeq) {
    return { kind: 'card', push: { taskId, seq, cardSeq, businessDate, card: card as unknown as DeskTask } };
  }
  return { kind: 'noCard', taskId };
}
