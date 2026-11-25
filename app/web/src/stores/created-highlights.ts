import { create } from 'zustand';

export const CREATED_HIGHLIGHT_MS = 2_600;

interface CreatedHighlightsState {
  taskIds: ReadonlySet<string>;
  focusPending: boolean;
  timer: ReturnType<typeof setTimeout> | null;
  show: (taskIds: readonly string[]) => void;
  consumeFocus: () => void;
  reset: () => void;
}

export const useCreatedHighlightsStore = create<CreatedHighlightsState>((set, get) => ({
  taskIds: new Set(),
  focusPending: false,
  timer: null,
  show: (taskIds) => {
    const previous = get().timer;
    if (previous) clearTimeout(previous);
    const timer = setTimeout(() => set({ taskIds: new Set(), timer: null }), CREATED_HIGHLIGHT_MS);
    set({ taskIds: new Set(taskIds), focusPending: true, timer });
  },
  consumeFocus: () => set({ focusPending: false }),
  reset: () => {
    const previous = get().timer;
    if (previous) clearTimeout(previous);
    set({ taskIds: new Set(), focusPending: false, timer: null });
  },
}));
