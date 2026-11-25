import { create } from 'zustand';
import { deskBoardQuery } from '../api/desk';
import { queryClient } from '../api/query-client';
import type { DeskTaskDetail } from '../api/types';

interface DeskPanelsState {
  detailTaskId: string | null;
  answerTaskId: string | null;
  createRequestOpen: boolean;
  createRequestRoomNo: string | null;
  openDetail: (taskId: string) => void;
  closeDetail: () => void;
  openAnswer: (taskId: string) => void;
  closeAnswer: () => void;
  openCreateRequest: () => void;
  closeCreateRequest: () => void;
  reset: () => void;
}

function roomNoOf(taskId: string | null): string | null {
  if (!taskId) return null;
  const onBoard = queryClient.getQueryData(deskBoardQuery.queryKey)?.tasks.find((task) => task.id === taskId);
  return onBoard?.roomNo ?? queryClient.getQueryData<DeskTaskDetail>(['desk-task', taskId])?.roomNo ?? null;
}

export const useDeskPanelsStore = create<DeskPanelsState>((set, get) => ({
  detailTaskId: null,
  answerTaskId: null,
  createRequestOpen: false,
  createRequestRoomNo: null,
  openDetail: (taskId) => set({ detailTaskId: taskId }),
  closeDetail: () => set({ detailTaskId: null }),
  openAnswer: (taskId) => set({ answerTaskId: taskId }),
  closeAnswer: () => set({ answerTaskId: null }),
  openCreateRequest: () => set({ createRequestOpen: true, createRequestRoomNo: roomNoOf(get().detailTaskId) }),
  closeCreateRequest: () => set({ createRequestOpen: false, createRequestRoomNo: null }),
  reset: () =>
    set({ detailTaskId: null, answerTaskId: null, createRequestOpen: false, createRequestRoomNo: null }),
}));
