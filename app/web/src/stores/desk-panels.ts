import { create } from 'zustand';

interface DeskPanelsState {
  detailTaskId: string | null;
  answerTaskId: string | null;
  openDetail: (taskId: string) => void;
  closeDetail: () => void;
  openAnswer: (taskId: string) => void;
  closeAnswer: () => void;
  reset: () => void;
}

export const useDeskPanelsStore = create<DeskPanelsState>((set) => ({
  detailTaskId: null,
  answerTaskId: null,
  openDetail: (taskId) => set({ detailTaskId: taskId }),
  closeDetail: () => set({ detailTaskId: null }),
  openAnswer: (taskId) => set({ answerTaskId: taskId }),
  closeAnswer: () => set({ answerTaskId: null }),
  reset: () => set({ detailTaskId: null, answerTaskId: null }),
}));
