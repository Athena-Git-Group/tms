import { create } from 'zustand';

/**
 * 外框上的詳情面板與當場回答面板現在開著哪一張單。
 * 只放在記憶體、不寫進任何儲存空間，所以重新整理後兩個面板都是關的。各種狀態的去留集中列在 `lib/persistence.ts`。
 */
interface DeskPanelsState {
  /** 詳情面板開著的單；沒開是 `null`。 */
  detailTaskId: string | null;
  /** 當場回答面板開著的單；沒開是 `null`。 */
  answerTaskId: string | null;
  openDetail: (taskId: string) => void;
  closeDetail: () => void;
  openAnswer: (taskId: string) => void;
  closeAnswer: () => void;
  /** 兩個面板都關掉，登出時用。 */
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
