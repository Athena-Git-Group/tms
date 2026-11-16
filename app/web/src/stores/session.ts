import { create } from 'zustand';
import type { LoginResponse } from '../api/types';
import { readJson, removeItem, writeJson } from '../lib/browser-storage';

/**
 * 登入狀態：後端的登入回應整份存在這個分頁的 `sessionStorage`。
 * 重新整理還在；關掉分頁就沒了；每個分頁各自一份，互不影響。
 * 不放 cookie、`localStorage` 或網址。
 */

/** 登入狀態就是登入回應本身（含憑證、姓名、角色、員工編號、飯店名稱、是否必須先改密碼）。 */
export type Session = LoginResponse;

/** `sessionStorage` 的鍵。值是登入回應的 JSON，沒有再包一層。 */
export const SESSION_STORAGE_KEY = 'tms.frontdesk.session';

const REQUIRED_TEXT_FIELDS = [
  'token',
  'expiresAt',
  'staffId',
  'name',
  'loginId',
  'hotelName',
  'role',
] as const satisfies readonly (keyof Session)[];

interface SessionState {
  session: Session | null;
  /** 登入成功、或設定新密碼成功後，用後端的回應換掉登入狀態。 */
  setSession: (session: Session) => void;
  /** 清掉這個分頁的登入狀態。登出要一併清掉的其他東西由登出那個功能自己處理。 */
  clearSession: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  session: readStoredSession(),
  setSession: (session) => {
    writeJson('session', SESSION_STORAGE_KEY, session);
    set({ session });
  },
  clearSession: () => {
    removeItem('session', SESSION_STORAGE_KEY);
    set({ session: null });
  },
}));

function readStoredSession(): Session | null {
  const stored = readJson('session', SESSION_STORAGE_KEY);
  return isSession(stored) ? stored : null;
}

/**
 * 帳號卡、身分卡與帶憑證的請求都直接拿契約必填的欄位用，缺一項畫面就會顯示不出來或送出壞掉的請求。
 * 所以缺欄位的內容寧可當作沒有登入，讓人重新登入一次。
 */
function isSession(value: unknown): value is Session {
  if (typeof value !== 'object' || value === null) return false;
  const session = value as Record<string, unknown>;
  return (
    REQUIRED_TEXT_FIELDS.every((field) => typeof session[field] === 'string') &&
    typeof session.mustChangePassword === 'boolean'
  );
}
