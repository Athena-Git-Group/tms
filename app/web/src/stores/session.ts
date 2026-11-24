import { create } from 'zustand';
import type { LoginResponse } from '../api/types';
import { readJson, removeItem, writeJson } from '../lib/browser-storage';
import { SESSION_STATE } from '../lib/persistence';

export type Session = LoginResponse & { hotelCode: string };

const REQUIRED_TEXT_FIELDS = [
  'token',
  'expiresAt',
  'staffId',
  'name',
  'loginId',
  'hotelName',
  'role',
  'hotelCode',
] as const satisfies readonly (keyof Session)[];

interface SessionState {
  session: Session | null;
  setSession: (response: LoginResponse, hotelCode: string) => void;
  clearSession: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  session: null,
  setSession: (response, hotelCode) => {
    const session: Session = { ...response, hotelCode };
    writeJson(SESSION_STATE.kind, SESSION_STATE.key, session);
    set({ session });
  },
  clearSession: () => {
    removeItem(SESSION_STATE.kind, SESSION_STATE.key);
    set({ session: null });
  },
}));

export function restoreSession(): void {
  useSessionStore.setState({ session: readStoredSession() });
}

function readStoredSession(): Session | null {
  const stored = readJson(SESSION_STATE.kind, SESSION_STATE.key);
  return isSession(stored) ? stored : null;
}

function isSession(value: unknown): value is Session {
  if (typeof value !== 'object' || value === null) return false;
  const session = value as Record<string, unknown>;
  return (
    REQUIRED_TEXT_FIELDS.every((field) => typeof session[field] === 'string') &&
    typeof session.mustChangePassword === 'boolean'
  );
}
