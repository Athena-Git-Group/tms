import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SESSION_STATE } from '../lib/persistence';

/** 單元測試沒有瀏覽器的儲存空間，用放在記憶體的簡易版代替；要在載入 store 之前裝好，store 建立時就會去讀。 */
function memoryStorage(): Storage {
  const items = new Map<string, string>();
  return {
    get length() {
      return items.size;
    },
    clear: () => items.clear(),
    getItem: (key) => items.get(key) ?? null,
    key: (index) => [...items.keys()][index] ?? null,
    removeItem: (key) => void items.delete(key),
    setItem: (key, value) => void items.set(key, value),
  };
}

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
  vi.resetModules();
  sessionStorage = memoryStorage();
  vi.stubGlobal('window', { sessionStorage, localStorage: memoryStorage() });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('登入狀態記下登入的飯店', () => {
  it('登入時把網址上的飯店識別代碼和登入回應一起存起來，重新整理後讀得回來', async () => {
    const first = await import('./session');
    first.useSessionStore.getState().setSession(loginResponse, 'athena');
    expect(JSON.parse(sessionStorage.getItem(SESSION_STATE.key)!)).toEqual({ ...loginResponse, hotelCode: 'athena' });

    vi.resetModules();
    const reloaded = await import('./session');
    expect(reloaded.useSessionStore.getState().session).toEqual({ ...loginResponse, hotelCode: 'athena' });
  });

  it('加上飯店識別代碼之前存的登入狀態分不出是哪一家，當作沒有登入', async () => {
    sessionStorage.setItem(SESSION_STATE.key, JSON.stringify(loginResponse));
    const { useSessionStore } = await import('./session');
    expect(useSessionStore.getState().session).toBeNull();
  });
});
