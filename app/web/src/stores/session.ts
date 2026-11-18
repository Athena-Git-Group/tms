import { create } from 'zustand';
import type { LoginResponse } from '../api/types';
import { readJson, removeItem, writeJson } from '../lib/browser-storage';
import { SESSION_STATE } from '../lib/persistence';

/**
 * 登入狀態：後端的登入回應整份，加上登入時網址上的飯店識別代碼，存在這個分頁的 `sessionStorage`。
 * 重新整理還在；關掉分頁就沒了；每個分頁各自一份，互不影響。
 * 不放 cookie、`localStorage` 或網址。鍵定在 `lib/persistence.ts`。
 */

/**
 * 登入狀態是登入回應本身（含憑證、姓名、角色、員工編號、飯店名稱、是否必須先改密碼），
 * 再加上登入的是哪一家飯店的識別代碼。飯店之間是隔離的：網址上的代碼和這裡記的不同時，路由那一層把這個分頁當成在那家飯店沒有登入。
 */
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
  /** 登入成功、或設定新密碼成功後，用後端的回應換掉登入狀態，並記下這次登入的是哪一家飯店。 */
  setSession: (response: LoginResponse, hotelCode: string) => void;
  /** 清掉這個分頁的登入狀態。登出要一併清掉的其他東西由登出那個功能自己處理。 */
  clearSession: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  session: readStoredSession(),
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

function readStoredSession(): Session | null {
  const stored = readJson(SESSION_STATE.kind, SESSION_STATE.key);
  return isSession(stored) ? stored : null;
}

/**
 * 帳號卡、身分卡與帶憑證的請求都直接拿契約必填的欄位用，缺一項畫面就會顯示不出來或送出壞掉的請求。
 * 所以缺欄位的內容寧可當作沒有登入，讓人重新登入一次。
 * 加上飯店識別代碼之前存的登入狀態沒有 `hotelCode`，分不出是哪一家飯店的，也照這條當作沒有登入。
 */
function isSession(value: unknown): value is Session {
  if (typeof value !== 'object' || value === null) return false;
  const session = value as Record<string, unknown>;
  return (
    REQUIRED_TEXT_FIELDS.every((field) => typeof session[field] === 'string') &&
    typeof session.mustChangePassword === 'boolean'
  );
}
