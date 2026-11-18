import { QueryClient } from '@tanstack/react-query';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { memoryStorage } from '../test-support/memory-storage';

const sessionStorage = memoryStorage();
const localStorage = memoryStorage();

beforeAll(() => {
  vi.stubGlobal('window', { sessionStorage, localStorage });
});

afterAll(() => {
  vi.unstubAllGlobals();
});

describe('logOut', () => {
  it('清掉看板的選擇、開著的面板、後端資料快取、登入頁提示與登入狀態，介面語言留著', async () => {
    const { BOARD_VIEW_STATE, SESSION_STATE, STAFF_LANGUAGE_STATE, LOGIN_PAGE_LANGUAGE_STATE, TAB_STORED_STATES } =
      await import('./persistence');
    const { useBoardViewStore } = await import('../stores/board-view');
    const { useLoginNoticeStore } = await import('../stores/login-notice');
    const { useDeskPanelsStore } = await import('../stores/desk-panels');
    const { useBoardDragStore } = await import('../stores/board-drag');
    const { useSessionStore } = await import('../stores/session');
    const { logOut } = await import('./log-out');

    const initialView = {
      lane: useBoardViewStore.getState().lane,
      cardFilter: useBoardViewStore.getState().cardFilter,
      roomSearch: useBoardViewStore.getState().roomSearch,
      sidebarOpenByUser: useBoardViewStore.getState().sidebarOpenByUser,
    };
    useBoardViewStore.getState().update({ lane: 'floor', cardFilter: 'pool', roomSearch: '1201', sidebarOpenByUser: true });
    useSessionStore.getState().setSession({
      token: 'token',
      expiresAt: '2026-11-17T10:00:00Z',
      staffId: 'staff-1',
      name: '王小明',
      loginId: 'F0101',
      hotelName: '測試飯店',
      role: 'frontdesk',
      mustChangePassword: false,
    }, 'test-hotel');
    useLoginNoticeStore.getState().leave('deskBlocked');
    useDeskPanelsStore.getState().openDetail('task-1');
    useDeskPanelsStore.getState().openAnswer('task-1');
    useBoardDragStore.getState().startDrag({
      id: 'task-1',
      displayNo: 'T-1',
      seq: 1,
      state: 'triage',
      answered: false,
      dept: 'frontdesk',
      type: 'other',
      source: 'guest',
      roomNo: '305',
      floor: 3,
      vip: false,
      title: '房間有怪聲',
      guestTyped: true,
      createdAt: '2026-10-26T06:25:00Z',
      updatedAt: '2026-10-26T06:25:00Z',
    });
    const languageKey = `${STAFF_LANGUAGE_STATE.keyPrefix}F0101`;
    localStorage.setItem(languageKey, 'ja');
    localStorage.setItem(LOGIN_PAGE_LANGUAGE_STATE.key, 'en');
    const queryClient = new QueryClient();
    queryClient.setQueryData(['desk-board'], { tickets: [] });

    expect(sessionStorage.getItem(BOARD_VIEW_STATE.key)).not.toBeNull();
    expect(sessionStorage.getItem(SESSION_STATE.key)).not.toBeNull();
    expect(useDeskPanelsStore.getState().detailTaskId).toBe('task-1');
    for (const { key } of TAB_STORED_STATES) {
      if (sessionStorage.getItem(key) === null) sessionStorage.setItem(key, '{}');
    }

    logOut(queryClient);

    const view = useBoardViewStore.getState();
    expect({
      lane: view.lane,
      cardFilter: view.cardFilter,
      roomSearch: view.roomSearch,
      sidebarOpenByUser: view.sidebarOpenByUser,
    }).toEqual(initialView);
    expect(sessionStorage.getItem(BOARD_VIEW_STATE.key)).toBeNull();
    expect(useDeskPanelsStore.getState().detailTaskId).toBeNull();
    expect(useDeskPanelsStore.getState().answerTaskId).toBeNull();
    expect(useBoardDragStore.getState().dragging).toBeNull();
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
    expect(useLoginNoticeStore.getState().pending).toBeNull();
    expect(useSessionStore.getState().session).toBeNull();
    expect(sessionStorage.getItem(SESSION_STATE.key)).toBeNull();
    expect(localStorage.getItem(languageKey)).toBe('ja');
    expect(localStorage.getItem(LOGIN_PAGE_LANGUAGE_STATE.key)).toBe('en');
    for (const { key } of TAB_STORED_STATES) {
      expect(sessionStorage.getItem(key)).toBeNull();
    }
  });
});
