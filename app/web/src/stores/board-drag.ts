import { create } from 'zustand';
import type { DeskTask } from '../api/types';

interface BoardDragState {
  dragging: DeskTask | null;
  startDrag: (task: DeskTask) => void;
  endDrag: () => void;
  reset: () => void;
}

export const useBoardDragStore = create<BoardDragState>((set) => ({
  dragging: null,
  startDrag: (task) => set({ dragging: task }),
  endDrag: () => set({ dragging: null }),
  reset: () => set({ dragging: null }),
}));
