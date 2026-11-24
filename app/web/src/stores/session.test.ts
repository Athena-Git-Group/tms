import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SESSION_STATE } from '../lib/persistence';
import { memoryStorage } from '../test-support/memory-storage';
import { restoreSession, useSessionStore } from './session';

const loginResponse = {
  token: 'token',
  expiresAt: '2026-11-17T10:00:00Z',
  staffId: 'staff-1',
  name: '王小明',
  loginId: 'F0101',
  hotelName: '雅典娜酒店',
  role: 'frontdesk',
  mustChangePassword: false,
} as const;

let sessionStorage: Storage;

beforeEach(() => {
  sessionStorage = memoryStorage();
  vi.stubGlobal('window', { sessionStorage, localStorage: memoryStorage() });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('登入狀態記下登入的飯店', () => {
  it('登入時把網址上的飯店識別代碼和登入回應一起存起來，重新整理後讀得回來', () => {
    useSessionStore.getState().setSession(loginResponse, 'athena');
    expect(JSON.parse(sessionStorage.getItem(SESSION_STATE.key)!)).toEqual({ ...loginResponse, hotelCode: 'athena' });

    useSessionStore.setState({ session: null });
    restoreSession();
    expect(useSessionStore.getState().session).toEqual({ ...loginResponse, hotelCode: 'athena' });
  });

  it('加上飯店識別代碼之前存的登入狀態分不出是哪一家，當作沒有登入', () => {
    sessionStorage.setItem(SESSION_STATE.key, JSON.stringify(loginResponse));
    restoreSession();
    expect(useSessionStore.getState().session).toBeNull();
  });
});
