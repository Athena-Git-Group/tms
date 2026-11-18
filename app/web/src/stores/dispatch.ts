import { create } from 'zustand';
import type { AssignDept, DispatchNotice } from '../lib/dispatch';

export const ASSIGN_TOAST_MS = 5_000;

export interface AssignToast {
  taskId: string;
  roomNo: string;
  dept: AssignDept;
}

interface DispatchState {
  toast: AssignToast | null;
  notice: DispatchNotice | null;
  showToast: (toast: AssignToast) => void;
  dismissToast: (taskId: string) => void;
  showNotice: (notice: DispatchNotice) => void;
  closeNotice: () => void;
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
