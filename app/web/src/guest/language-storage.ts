import { isLanguage, type Language } from '../i18n/languages';
import { readText, writeText } from '../lib/browser-storage';

const GUEST_LANGUAGE_KEY = 'tms.guest.language';

export function readGuestLanguage(): Language | null {
  const value = readText('local', GUEST_LANGUAGE_KEY);
  return isLanguage(value) ? value : null;
}

export function writeGuestLanguage(language: Language): void {
  writeText('local', GUEST_LANGUAGE_KEY, language);
}
