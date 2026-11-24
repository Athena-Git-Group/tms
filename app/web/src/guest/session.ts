import { create } from 'zustand';
import { readJson, removeItem, writeJson } from '../lib/browser-storage';
import type { ScanResponse } from './api/types';

/**
 * 住客的 session 跟著分頁走：存 `sessionStorage`，鍵帶 QR 代碼。同一個分頁先後開兩間房的網址時，
 * 不會拿上一間房的 session 去打下一間房；鍵也和櫃檯的登入狀態不同，同一支手機上兩邊互相讀不到。
 * 儲存空間被封鎖時退回只放記憶體，重新整理就要再換一次 session，但頁面照常能用。
 */
const KEY_PREFIX = 'tms.guest.session.';

const inMemory = new Map<string, ScanResponse>();

function keyOf(qrToken: string): string {
  return KEY_PREFIX + qrToken;
}

export function readGuestSession(qrToken: string): ScanResponse | null {
  const stored = readJson('session', keyOf(qrToken));
  if (isScanResponse(stored)) return stored;
  return inMemory.get(qrToken) ?? null;
}

export function writeGuestSession(qrToken: string, scan: ScanResponse): void {
  inMemory.set(qrToken, scan);
  writeJson('session', keyOf(qrToken), scan);
}

export function clearGuestSession(qrToken: string): void {
  inMemory.delete(qrToken);
  removeItem('session', keyOf(qrToken));
}

function isScanResponse(value: unknown): value is ScanResponse {
  if (typeof value !== 'object' || value === null) return false;
  const scan = value as Record<string, unknown>;
  return (
    typeof scan.sessionToken === 'string' &&
    typeof scan.expiresAt === 'string' &&
    typeof scan.roomNo === 'string' &&
    Array.isArray(scan.infoPages) &&
    Array.isArray(scan.menu)
  );
}

export type GuestSession = ScanResponse & { qrToken: string };

interface GuestSessionState {
  session: GuestSession | null;
  setSession: (qrToken: string, scan: ScanResponse) => void;
  clearSession: () => void;
}

/** 目前網址上這枚代碼的 session，給各頁讀房號、服務項目、住宿指南與要帶出的 session。 */
export const useGuestSessionStore = create<GuestSessionState>((set, get) => ({
  session: null,
  setSession: (qrToken, scan) => {
    writeGuestSession(qrToken, scan);
    set({ session: { ...scan, qrToken } });
  },
  clearSession: () => {
    const current = get().session;
    if (current) clearGuestSession(current.qrToken);
    set({ session: null });
  },
}));

/** 進入層換到 session 之後才畫的頁用它；那時一定有 session。 */
export function useReadySession(): GuestSession {
  const session = useGuestSessionStore((state) => state.session);
  if (!session) throw new Error('住客頁在換到 session 之前就畫了');
  return session;
}

/** 送出當下手上的 session：中途被重換過時要用新的那一把。 */
export function currentSessionToken(): string {
  const session = useGuestSessionStore.getState().session;
  if (!session) throw new Error('住客頁在換到 session 之前就送出');
  return session.sessionToken;
}
