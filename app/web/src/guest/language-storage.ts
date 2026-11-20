import { isLanguage, type Language } from '../i18n/languages';
import { readText, writeText } from '../lib/browser-storage';

/** 住客選過的介面語言，一支手機一筆；鍵和櫃檯的語言鍵不同，兩邊選的語言互不影響。 */
const GUEST_LANGUAGE_KEY = 'tms.guest.language';

export function readGuestLanguage(): Language | null {
  const value = readText('local', GUEST_LANGUAGE_KEY);
  return isLanguage(value) ? value : null;
}

export function writeGuestLanguage(language: Language): void {
  writeText('local', GUEST_LANGUAGE_KEY, language);
}
