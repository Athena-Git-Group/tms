import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { memoryStorage } from '../test-support/memory-storage';
import type { ScanResponse } from './api/types';

function scan(roomNo: string, sessionToken: string): ScanResponse {
  return {
    sessionToken,
    expiresAt: '2026-11-22T03:00:00Z',
    roomNo,
    infoPages: [{ key: 'wifi', title: 'Wi‑Fi', body: '密碼 12345678' }],
    menu: [{ key: '毛巾', label: '毛巾' }],
  };
}

let sessionStorage: Storage;

beforeEach(() => {
  vi.resetModules();
  sessionStorage = memoryStorage();
  vi.stubGlobal('window', { sessionStorage, localStorage: memoryStorage() });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('住客 session 依代碼分開存', () => {
  it('鍵帶代碼，重新載入程式後讀得回來', async () => {
    const first = await import('./session');
    first.writeGuestSession('qr-311', scan('311', 'gs_a'));
    expect(JSON.parse(sessionStorage.getItem('tms.guest.session.qr-311')!)).toEqual(scan('311', 'gs_a'));

    vi.resetModules();
    const reloaded = await import('./session');
    expect(reloaded.readGuestSession('qr-311')).toEqual(scan('311', 'gs_a'));
  });

  it('兩枚代碼互不覆蓋', async () => {
    const { readGuestSession, writeGuestSession, clearGuestSession } = await import('./session');
    writeGuestSession('qr-311', scan('311', 'gs_a'));
    writeGuestSession('qr-308', scan('308', 'gs_b'));
    expect(readGuestSession('qr-311')?.sessionToken).toBe('gs_a');
    expect(readGuestSession('qr-308')?.sessionToken).toBe('gs_b');

    clearGuestSession('qr-311');
    expect(readGuestSession('qr-311')).toBeNull();
    expect(readGuestSession('qr-308')?.sessionToken).toBe('gs_b');
  });

  it('不讀櫃檯的登入狀態', async () => {
    sessionStorage.setItem('tms.frontdesk.session', JSON.stringify({ token: 'desk' }));
    const { readGuestSession } = await import('./session');
    expect(readGuestSession('qr-311')).toBeNull();
  });

  it('儲存空間一取用就丟例外時，仍讀得回放在記憶體裡的值', async () => {
    vi.stubGlobal('window', {
      get sessionStorage(): Storage {
        throw new Error('SecurityError');
      },
      get localStorage(): Storage {
        throw new Error('SecurityError');
      },
    });
    const { readGuestSession, writeGuestSession } = await import('./session');
    writeGuestSession('qr-311', scan('311', 'gs_a'));
    expect(readGuestSession('qr-311')?.sessionToken).toBe('gs_a');
  });

  it('store 提供目前這枚代碼的 session，清掉時連儲存的一起清', async () => {
    const { useGuestSessionStore, readGuestSession } = await import('./session');
    useGuestSessionStore.getState().setSession('qr-311', scan('311', 'gs_a'));
    expect(useGuestSessionStore.getState().session).toMatchObject({ qrToken: 'qr-311', roomNo: '311', sessionToken: 'gs_a' });

    useGuestSessionStore.getState().clearSession();
    expect(useGuestSessionStore.getState().session).toBeNull();
    expect(readGuestSession('qr-311')).toBeNull();
  });
});
