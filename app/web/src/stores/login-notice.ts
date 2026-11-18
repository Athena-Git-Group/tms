import { create } from 'zustand';

/**
 * 回到登入頁時要先顯示的提示。目前只有一種：不能用櫃檯畫面的角色剛設定完新密碼，
 * 改密碼畫面清掉登入狀態之後留下這一筆，登入頁打開時讀到它就顯示提示卡。
 * 這一筆只放在記憶體，重新整理之後登入頁照一般的樣子顯示。各種狀態的去留集中列在 `lib/persistence.ts`。
 */
export type PendingLoginNotice = 'deskBlocked';

interface LoginNoticeState {
  pending: PendingLoginNotice | null;
  leave: (notice: PendingLoginNotice) => void;
  clear: () => void;
}

export const useLoginNoticeStore = create<LoginNoticeState>((set) => ({
  pending: null,
  leave: (notice) => set({ pending: notice }),
  clear: () => set({ pending: null }),
}));
