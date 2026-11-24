import { queryOptions, type QueryClient } from '@tanstack/react-query';
import { applyCardPush, mergeFullRead, type CardPush, type ReceivedPushes } from '../lib/board-sync';
import { useServerClockStore } from '../stores/server-clock';
import { apiGet, apiWrite } from './client';
import type {
  DeskAnswerRequest,
  DeskAssignRequest,
  DeskBoard,
  DeskCreateRequestsRequest,
  DeskCreateRequestsResult,
  DeskQuickReply,
  DeskRoom,
  DeskTask,
  DeskTaskDetail,
} from './types';

function syncServerClock<T extends { serverTime: string }>(response: T): T {
  useServerClockStore.getState().syncWith(response.serverTime, new Date());
  return response;
}

export function readDeskBoard(signal?: AbortSignal): Promise<DeskBoard> {
  return apiGet<DeskBoard>('/desk/board', { signal }).then(syncServerClock);
}

interface PushLog {
  pushes: ReceivedPushes;
  lastOrder: number;
}

const pushLogs = new WeakMap<QueryClient, PushLog>();

function pushLogOf(client: QueryClient): PushLog {
  let log = pushLogs.get(client);
  if (!log) {
    log = { pushes: new Map(), lastOrder: 0 };
    pushLogs.set(client, log);
  }
  return log;
}

export const deskBoardQuery = queryOptions({
  queryKey: ['desk-board'],
  queryFn: async ({ signal, client }) => {
    const startedOrder = pushLogOf(client).lastOrder;
    const fresh = await readDeskBoard(signal);
    const log = pushLogOf(client);
    const merged = mergeFullRead(fresh, log.pushes, startedOrder);
    log.pushes = merged.pushes;
    return merged.board;
  },
  retry: false,
});

const boardReadCancels = new WeakMap<QueryClient, number>();

export function boardReadCancelCount(queryClient: QueryClient): number {
  return boardReadCancels.get(queryClient) ?? 0;
}

export async function cancelBoardReadForDispatch(queryClient: QueryClient): Promise<void> {
  boardReadCancels.set(queryClient, boardReadCancelCount(queryClient) + 1);
  await queryClient.cancelQueries({ queryKey: deskBoardQuery.queryKey });
}

export function receiveCardPush(queryClient: QueryClient, push: CardPush): { needsFullRead: boolean } {
  const board = queryClient.getQueryData(deskBoardQuery.queryKey);
  if (!board) return { needsFullRead: true };
  const log = pushLogOf(queryClient);
  log.lastOrder += 1;
  const applied = applyCardPush(board, log.pushes, push, log.lastOrder);
  log.pushes = applied.pushes;
  if (applied.board !== board) queryClient.setQueryData(deskBoardQuery.queryKey, applied.board);
  return { needsFullRead: applied.needsFullRead };
}

export function receiveCreatedCards(queryClient: QueryClient, tasks: readonly DeskTask[]): void {
  let needsFullRead = false;
  for (const task of tasks) {
    const board = queryClient.getQueryData(deskBoardQuery.queryKey);
    const push: CardPush = {
      taskId: task.id,
      seq: task.seq,
      cardSeq: task.seq,
      businessDate: board?.businessDate ?? '',
      change: { kind: 'upsert', card: task },
    };
    if (receiveCardPush(queryClient, push).needsFullRead) needsFullRead = true;
  }
  if (needsFullRead) void queryClient.invalidateQueries({ queryKey: deskBoardQuery.queryKey });
}

export function readDeskTask(taskId: string, signal?: AbortSignal): Promise<DeskTaskDetail> {
  return apiGet<DeskTaskDetail>(`/desk/tasks/${encodeURIComponent(taskId)}`, { signal }).then(syncServerClock);
}

export function listDeskQuickReplies(): Promise<DeskQuickReply[]> {
  return apiGet<DeskQuickReply[]>('/desk/quick-replies');
}

export function answerDeskTask(taskId: string, request: DeskAnswerRequest): Promise<DeskTask> {
  return apiWrite<DeskTask>(`/desk/tasks/${encodeURIComponent(taskId)}/answer`, request);
}

export function assignDeskTask(taskId: string, request: DeskAssignRequest): Promise<DeskTask> {
  return apiWrite<DeskTask>(`/desk/tasks/${encodeURIComponent(taskId)}/assign`, request);
}

export function undoDeskAssign(taskId: string): Promise<DeskTask> {
  return apiWrite<DeskTask>(`/desk/tasks/${encodeURIComponent(taskId)}/assign/undo`);
}

export function listDeskRooms(signal?: AbortSignal): Promise<DeskRoom[]> {
  return apiGet<DeskRoom[]>('/desk/rooms', { signal });
}

export function createDeskRequests(
  request: DeskCreateRequestsRequest,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<DeskCreateRequestsResult> {
  return apiWrite<DeskCreateRequestsResult>('/desk/requests', request, { idempotencyKey, signal });
}
