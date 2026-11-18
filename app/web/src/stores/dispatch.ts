import { create } from 'zustand';
import type { AssignDept, DispatchNotice } from '../lib/dispatch';

/** 浮條與「復原」出現多久，之後自己消失。 */
export const ASSIGN_TOAST_MS = 5_000;

/** 畫面底部浮條上的那一次派單。 */
export interface AssignToast {
  taskId: string;
  roomNo: string;
  dept: AssignDept;
}

/**
 * 派單浮條與派單、復原失敗的對話框。浮條掛在外框，跟著人走不跟著頁。
 * 同一時間只有一條浮條：5 秒內又派了一張，就換成新的那一張，前一張不能在這裡復原了。
 * 只放在記憶體，重新整理或登出就沒有了。各種狀態的去留集中列在 `lib/persistence.ts`。
 */
interface DispatchState {
  toast: AssignToast | null;
  notice: DispatchNotice | null;
  /** 出現浮條，5 秒後自己收掉；之前那一條直接被換掉。 */
  showToast: (toast: AssignToast) => void;
  /** 收掉這張單的浮條；畫面上的已經換成別張時不動。 */
  dismissToast: (taskId: string) => void;
  showNotice: (notice: DispatchNotice) => void;
  closeNotice: () => void;
  /** 浮條與對話框都收掉，登出時用。 */
  reset: () => void;
}

let toastTimer: ReturnType<typeof setTimeout> | undefined;

function stopToastTimer() {
  clearTimeout(toastTimer);
  toastTimer = undefined;
}

export const useDispatchStore = create<DispatchState>((set, get) => ({
  toast: null,
  notice: null,
  showToast: (toast) => {
    stopToastTimer();
    set({ toast });
    toastTimer = setTimeout(() => get().dismissToast(toast.taskId), ASSIGN_TOAST_MS);
  },
  dismissToast: (taskId) => {
    if (get().toast?.taskId !== taskId) return;
    stopToastTimer();
    set({ toast: null });
  },
  showNotice: (notice) => set({ notice }),
  closeNotice: () => set({ notice: null }),
  reset: () => {
    stopToastTimer();
    set({ toast: null, notice: null });
  },
}));
