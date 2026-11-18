import { readText, writeText, type StorageKind } from '../lib/browser-storage';
import { LOGIN_PAGE_LANGUAGE_STATE, STAFF_LANGUAGE_STATE } from '../lib/persistence';
import { isLanguage, type Language } from './languages';

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
