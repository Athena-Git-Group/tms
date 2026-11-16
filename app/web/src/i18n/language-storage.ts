import { readText, writeText } from '../lib/browser-storage';
import { isLanguage, type Language } from './languages';

/**
 * 介面語言記在這台裝置的 `localStorage`：關掉瀏覽器還在、分頁之間共用，登出也不清。
 * 和登入狀態分開存，是因為語言是「這台裝置上每個人各自的習慣」，不跟著某一次登入走。
 * 存的值就是語言代碼本身（`zh-TW`、`en`、`ja`、`vi`），沒有再包一層。
 *
 * 這裡只有讀寫；誰的語言優先、什麼時候寫入，由登入頁與帳號面板的語言功能決定。
 */

/** 登入前沒有身分，所以登入頁現在用的語言另記一筆。 */
export const LOGIN_PAGE_LANGUAGE_KEY = 'tms.frontdesk.language.login-page';

/** 每個人一筆，鍵是這個前綴加員工編號，例如 `tms.frontdesk.language.staff.F0101`。 */
export const STAFF_LANGUAGE_KEY_PREFIX = 'tms.frontdesk.language.staff.';

export function readLoginPageLanguage(): Language | null {
  return read(LOGIN_PAGE_LANGUAGE_KEY);
}

export function writeLoginPageLanguage(language: Language): void {
  writeText('local', LOGIN_PAGE_LANGUAGE_KEY, language);
}

export function readStaffLanguage(loginId: string): Language | null {
  return read(STAFF_LANGUAGE_KEY_PREFIX + loginId);
}

export function writeStaffLanguage(loginId: string, language: Language): void {
  writeText('local', STAFF_LANGUAGE_KEY_PREFIX + loginId, language);
}

function read(key: string): Language | null {
  const value = readText('local', key);
  return isLanguage(value) ? value : null;
}
