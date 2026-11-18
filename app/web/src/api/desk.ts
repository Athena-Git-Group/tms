import { queryOptions } from '@tanstack/react-query';
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
export function readDeskBoard(): Promise<DeskBoard> {
  return apiGet<DeskBoard>('/desk/board').then(syncServerClock);
}

/**
 * 看板資料的查詢設定，外框、總覽看板、頁副標都用這一份，讀到的是同一份快取。
 * 失敗不在背後自動重試（`retry: false`）：第一次讀不到就讓畫面顯示讀取失敗，
 * 之後每 5 秒再讀一次的間隔只掛在外框的那一個查詢上（見 `DeskShell`），不會有好幾個計時器各讀各的。
 */
export const deskBoardQuery = queryOptions({
  queryKey: ['desk-board'],
  queryFn: readDeskBoard,
  retry: false,
});

/** 單筆詳情與歷程，詳情面板與當場回答面板用。 */
export function readDeskTask(taskId: string): Promise<DeskTaskDetail> {
  return apiGet<DeskTaskDetail>(`/desk/tasks/${encodeURIComponent(taskId)}`).then(syncServerClock);
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
