import { readText, writeText, type StorageKind } from '../lib/browser-storage';
import { LOGIN_PAGE_LANGUAGE_STATE, STAFF_LANGUAGE_STATE } from '../lib/persistence';
import { isLanguage, type Language } from './languages';

/**
 * 介面語言記在這台裝置的 `localStorage`：關掉瀏覽器還在、分頁之間共用，登出也不清。鍵定在 `lib/persistence.ts`。
 * 和登入狀態分開存，是因為語言是「這台裝置上每個人各自的習慣」，不跟著某一次登入走。
 * 存的值就是語言代碼本身（`zh-TW`、`en`、`ja`、`vi`），沒有再包一層。
 *
 * 這裡只有讀寫；誰的語言優先、什麼時候寫入，由登入頁與帳號面板的語言功能決定。
 */

export function readLoginPageLanguage(): Language | null {
  return read(LOGIN_PAGE_LANGUAGE_STATE.kind, LOGIN_PAGE_LANGUAGE_STATE.key);
}

export function writeLoginPageLanguage(language: Language): void {
  writeText(LOGIN_PAGE_LANGUAGE_STATE.kind, LOGIN_PAGE_LANGUAGE_STATE.key, language);
}

export function readStaffLanguage(loginId: string): Language | null {
  return read(STAFF_LANGUAGE_STATE.kind, STAFF_LANGUAGE_STATE.keyPrefix + loginId);
}

export function writeStaffLanguage(loginId: string, language: Language): void {
  writeText(STAFF_LANGUAGE_STATE.kind, STAFF_LANGUAGE_STATE.keyPrefix + loginId, language);
}

function read(kind: StorageKind, key: string): Language | null {
  const value = readText(kind, key);
  return isLanguage(value) ? value : null;
}
