import type { QueryClient } from '@tanstack/react-query';
import { useBoardDragStore } from '../stores/board-drag';
import { useBoardViewStore } from '../stores/board-view';
import { useCreatedHighlightsStore } from '../stores/created-highlights';
import { useDeskPanelsStore } from '../stores/desk-panels';
import { useDispatchStore } from '../stores/dispatch';
import { useLoginNoticeStore } from '../stores/login-notice';
import { useSessionStore } from '../stores/session';

export function logOut(queryClient: QueryClient): void {
  useBoardViewStore.getState().reset();
  useDeskPanelsStore.getState().reset();
  useCreatedHighlightsStore.getState().reset();
  useBoardDragStore.getState().reset();
  useDispatchStore.getState().reset();
  queryClient.clear();
  useLoginNoticeStore.getState().clear();
  useSessionStore.getState().clearSession();
}
