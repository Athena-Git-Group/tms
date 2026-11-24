import { isDeskTask } from '../../api/desk-task-shape';
import type { CardPush } from '../../lib/board-sync';

export type StreamTask = { kind: 'card'; push: CardPush } | { kind: 'noCard'; taskId: string | null };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isStep(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0;
}

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
  if (board === 'remove') {
    return { kind: 'card', push: { taskId, seq, cardSeq, businessDate, change: { kind: 'removedForBusinessDay' } } };
  }
  if (board === 'upsert' && isDeskTask(card) && card.id === taskId && card.seq === cardSeq) {
    return { kind: 'card', push: { taskId, seq, cardSeq, businessDate, change: { kind: 'upsert', card } } };
  }
  return { kind: 'noCard', taskId };
}
