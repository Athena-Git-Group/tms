import type { i18n as I18n } from 'i18next';
import { matchBrowserLanguage } from '../lib/browser-language';
import { useSessionStore, type Session } from '../stores/session';
import {
  readLoginPageLanguage,
  readStaffLanguage,
  writeLoginPageLanguage,
  writeStaffLanguage,
} from './language-storage';
import { DEFAULT_LANGUAGE, isLanguage, type Language } from './languages';

export function languageBeforeLogin(): Language {
  return readLoginPageLanguage() ?? matchBrowserLanguage(browserLanguages());
}

function languageOnStart(session: Session | null): Language {
  if (session === null) return languageBeforeLogin();
  return readStaffLanguage(session.loginId) ?? languageBeforeLogin();
}

export function chooseLanguage(i18n: I18n, language: Language): void {
  const session = useSessionStore.getState().session;
  if (session !== null) {
    writeStaffLanguage(session.loginId, language);
  }
  writeLoginPageLanguage(language);
  void i18n.changeLanguage(language);
}

export function initialLanguage(): Language {
  return languageOnStart(useSessionStore.getState().session);
}

export function followSession(i18n: I18n): void {
  syncDocumentLanguage(i18n.language);
  i18n.on('languageChanged', syncDocumentLanguage);

  useSessionStore.subscribe((state, previous) => {
    const before = previous.session?.loginId ?? null;
    const after = state.session?.loginId ?? null;
    if (before === after) return;
    const current = currentLanguage(i18n);
    if (before !== null) {
      writeLoginPageLanguage(current);
    }
    if (after !== null) {
      const own = readStaffLanguage(after);
      if (own === null) {
        writeStaffLanguage(after, current);
      } else if (own !== current) {
        void i18n.changeLanguage(own);
      }
    }
  });
}

function currentLanguage(i18n: I18n): Language {
  return isLanguage(i18n.language) ? i18n.language : DEFAULT_LANGUAGE;
}

function syncDocumentLanguage(language: string): void {
  if (typeof document !== 'undefined') document.documentElement.lang = language;
}

function browserLanguages(): readonly string[] {
  if (typeof navigator === 'undefined') return [];
  if (navigator.languages && navigator.languages.length > 0) return navigator.languages;
  return navigator.language ? [navigator.language] : [];
}
