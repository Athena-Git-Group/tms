import type { DeskBoard, DeskTask } from '../api/types';
import { utcOffsetMinutes } from './server-clock';

export type CardChange = { kind: 'upsert'; card: DeskTask } | { kind: 'removedForBusinessDay' };

export interface CardPush {
  taskId: string;
  seq: number;
  cardSeq: number;
  businessDate: string;
  change: CardChange;
}

export interface ReceivedPush {
  cardSeq: number;
  businessDate: string;
  change: CardChange;
  receivedOrder: number;
}

export type ReceivedPushes = ReadonlyMap<string, ReceivedPush>;

function knownSeq(board: DeskBoard, pushes: ReceivedPushes, taskId: string): number | undefined {
  const onBoard = board.tasks.find((task) => task.id === taskId)?.seq;
  const pushed = pushes.get(taskId)?.cardSeq;
  if (onBoard === undefined) return pushed;
  if (pushed === undefined) return onBoard;
  return Math.max(onBoard, pushed);
}

function withChange(board: DeskBoard, taskId: string, change: CardChange): DeskBoard {
  const rest = board.tasks.filter((task) => task.id !== taskId);
  if (change.kind === 'removedForBusinessDay') {
    return rest.length === board.tasks.length ? board : { ...board, tasks: rest };
  }
  const { card } = change;
  if (rest.length === board.tasks.length) return { ...board, tasks: [...board.tasks, card] };
  return { ...board, tasks: board.tasks.map((task) => (task.id === taskId ? card : task)) };
}

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
  next.set(push.taskId, { cardSeq: push.cardSeq, businessDate: push.businessDate, change: push.change, receivedOrder });
  return {
    board: withChange(board, push.taskId, push.change),
    pushes: next,
    needsFullRead: known !== undefined && push.seq > known + 1,
  };
}

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
      if (freshSeq === undefined || freshSeq < push.cardSeq) board = withChange(board, taskId, push.change);
      kept.set(taskId, push);
    } else if (push.change.kind === 'removedForBusinessDay') {
      kept.set(taskId, push);
    }
  }
  return { board, pushes: kept };
}

export const BUSINESS_DAY_RETRY_MS = 60_000;

export function businessDayRereadDelayMs(board: Pick<DeskBoard, 'businessDate' | 'serverTime'>, nowMs: number): number | null {
  const offset = utcOffsetMinutes(board.serverTime);
  const date = /^(\d{4})-(\d{2})-(\d{2})$/.exec(board.businessDate);
  if (offset === null || !date) return null;
  const nextMidnight = Date.UTC(Number(date[1]), Number(date[2]) - 1, Number(date[3]) + 1) - offset * 60_000;
  const delay = nextMidnight - nowMs;
  return delay > 0 ? delay : BUSINESS_DAY_RETRY_MS;
}
