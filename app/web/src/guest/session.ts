import { create } from 'zustand';
import { readJson, removeItem, writeJson } from '../lib/browser-storage';
import type { ScanResponse } from './api/types';

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

export function useReadySession(): GuestSession {
  const session = useGuestSessionStore((state) => state.session);
  if (!session) throw new Error('住客頁在換到 session 之前就畫了');
  return session;
}

export function currentSessionToken(): string {
  const session = useGuestSessionStore.getState().session;
  if (!session) throw new Error('住客頁在換到 session 之前就送出');
  return session.sessionToken;
}
