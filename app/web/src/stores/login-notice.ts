import { create } from 'zustand';

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
