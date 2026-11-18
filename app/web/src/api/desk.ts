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

/** 回應帶著後端時間時，記下收到的當下用來校正畫面共用的「現在」。 */
function syncServerClock<T extends { serverTime: string }>(response: T): T {
  useServerClockStore.getState().syncWith(response.serverTime, new Date());
  return response;
}

/** 總覽看板與待派單頁共用的那一份看板資料，一次回完、沒有查詢參數。 */
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

/**
 * 看板資料的查詢設定，外框、總覽看板、頁副標都用這一份，讀到的是同一份快取。
 * 失敗不在背後自動重試（`retry: false`）：第一次讀不到就讓畫面顯示讀取失敗，
 * 之後每隔 `REREAD_INTERVAL_MS` 再讀一次的間隔只掛在外框的那一個查詢上（見 `DeskShell`），不會有好幾個計時器各讀各的。
 * 讀回來的那一份照 `mergeFullRead` 和讀取期間收到的卡片合併，才放進快取。
 */
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

/** 單筆詳情與歷程，詳情面板與當場回答面板用。 */
export function readDeskTask(taskId: string, signal?: AbortSignal): Promise<DeskTaskDetail> {
  return apiGet<DeskTaskDetail>(`/desk/tasks/${encodeURIComponent(taskId)}`, { signal }).then(syncServerClock);
}

/** 當場回答面板的常用回覆，照飯店設定的順序；飯店沒有設定時是空陣列。 */
export function listDeskQuickReplies(): Promise<DeskQuickReply[]> {
  return apiGet<DeskQuickReply[]>('/desk/quick-replies');
}

/**
 * 當場回答並結案，回結案後的這張單。沒有復原。
 * 每呼叫一次就產生一把新的防重送鍵。
 */
export function answerDeskTask(taskId: string, request: DeskAnswerRequest): Promise<DeskTask> {
  return apiWrite<DeskTask>(`/desk/tasks/${encodeURIComponent(taskId)}/answer`, request);
}

/**
 * 把待派單派給房務或工務，回派出後的這張單。
 * 每呼叫一次就產生一把新的防重送鍵，所以按一次（或拖一次）就是一次派單。
 */
export function assignDeskTask(taskId: string, request: DeskAssignRequest): Promise<DeskTask> {
  return apiWrite<DeskTask>(`/desk/tasks/${encodeURIComponent(taskId)}/assign`, request);
}

/**
 * 把剛派出的單收回待派單，回收回後的這張單。
 * 被拒絕時 `ApiError.code` 說明原因：已被同仁認領、住客已收回、超過時限或其他狀態已變。
 */
export function undoDeskAssign(taskId: string): Promise<DeskTask> {
  return apiWrite<DeskTask>(`/desk/tasks/${encodeURIComponent(taskId)}/assign/undo`);
}
