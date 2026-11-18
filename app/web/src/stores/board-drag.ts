import { create } from 'zustand';
import type { DeskTask } from '../api/types';

/**
 * 總覽看板上正在拖的那張單。控制列的提示、「待認領」欄的拖放區、其他格變淡、跟著游標走的那張卡片都讀它。
 * 只在拖曳派單那一層（`pages/board/board-drag.tsx` 的 `BoardDragDispatch`）寫入：起拖時放進來，放開、取消、看板離開畫面時拿掉。
 * 只放在記憶體、不寫進任何儲存空間。各種狀態的去留集中列在 `lib/persistence.ts`。
 */
interface BoardDragState {
  /** 正在拖的那張單；沒有人在拖時是 `null`。 */
  dragging: DeskTask | null;
  startDrag: (task: DeskTask) => void;
  endDrag: () => void;
  /** 回到沒有人在拖，登出時用。 */
  reset: () => void;
}

export const useBoardDragStore = create<BoardDragState>((set) => ({
  dragging: null,
  startDrag: (task) => set({ dragging: task }),
  endDrag: () => set({ dragging: null }),
  reset: () => set({ dragging: null }),
}));
