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

function syncServerClock<T extends { serverTime: string }>(response: T): T {
  useServerClockStore.getState().syncWith(response.serverTime, new Date());
  return response;
}

export function readDeskBoard(): Promise<DeskBoard> {
  return apiGet<DeskBoard>('/desk/board').then(syncServerClock);
}

export const deskBoardQuery = queryOptions({
  queryKey: ['desk-board'],
  queryFn: readDeskBoard,
  retry: false,
});

export function readDeskTask(taskId: string): Promise<DeskTaskDetail> {
  return apiGet<DeskTaskDetail>(`/desk/tasks/${encodeURIComponent(taskId)}`).then(syncServerClock);
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
