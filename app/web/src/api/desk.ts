import { queryOptions, type QueryClient } from '@tanstack/react-query';
import { applyCardPush, mergeFullRead, type CardPush, type ReceivedPushes } from '../lib/board-sync';
import { useServerClockStore } from '../stores/server-clock';
import { apiGet, apiWrite } from './client';
import type {
  DeskAnswerRequest,
  DeskAssignRequest,
  DeskBoard,
  DeskQuickReply,
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

/**
 * 每份快取各自記著即時訊號換上過的卡片（規則見 `lib/board-sync.ts`）。
 * 記在快取旁邊而不是連線裡，因為整份重讀不管是誰發的（訊號、外框定時重讀、派單之後），讀回來時都要用它比新舊。
 */
const receivedPushes = new WeakMap<QueryClient, ReceivedPushes>();

/** 收到卡片與開始整份重讀共用的先後順序，只拿來判斷卡片是不是在某次讀取開始之後才收到。 */
let receiveOrder = 0;

export const deskBoardQuery = queryOptions({
  queryKey: ['desk-board'],
  queryFn: async ({ signal, client }) => {
    const startedOrder = receiveOrder;
    const fresh = await readDeskBoard(signal);
    const merged = mergeFullRead(fresh, receivedPushes.get(client) ?? new Map(), startedOrder);
    receivedPushes.set(client, merged.pushes);
    return merged.board;
  },
  retry: false,
});

/**
 * 把即時訊號帶來的卡片照 `applyCardPush` 的規則換進看板快取，回傳要不要另外整份重讀。
 * 還沒有讀到過看板時無從比起，一律要整份重讀。
 */
export function receiveCardPush(queryClient: QueryClient, push: CardPush): { needsFullRead: boolean } {
  const board = queryClient.getQueryData(deskBoardQuery.queryKey);
  if (!board) return { needsFullRead: true };
  receiveOrder += 1;
  const applied = applyCardPush(board, receivedPushes.get(queryClient) ?? new Map(), push, receiveOrder);
  receivedPushes.set(queryClient, applied.pushes);
  if (applied.board !== board) queryClient.setQueryData(deskBoardQuery.queryKey, applied.board);
  return { needsFullRead: applied.needsFullRead };
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
