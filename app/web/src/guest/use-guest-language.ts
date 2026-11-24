import { useCallback, useLayoutEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { Language } from '../i18n/languages';
import { readGuestLanguage, writeGuestLanguage } from './language-storage';
import { guestLanguageFor } from './lib/language';

function browserLanguages(): readonly string[] {
  if (typeof navigator === 'undefined') return [];
  if (navigator.languages && navigator.languages.length > 0) return navigator.languages;
  return navigator.language ? [navigator.language] : [];
}

function apply(i18n: ReturnType<typeof useTranslation>['i18n'], language: Language): void {
  void i18n.changeLanguage(language);
  document.documentElement.lang = language;
}

export function useGuestLanguage(): void {
  const { i18n } = useTranslation();
  useLayoutEffect(() => apply(i18n, readGuestLanguage() ?? guestLanguageFor(browserLanguages())), [i18n]);
}

export function useSetGuestLanguage(): (language: Language) => void {
  const { i18n } = useTranslation();
  return useCallback(
    (language: Language) => {
      writeGuestLanguage(language);
      apply(i18n, language);
    },
    [i18n],
  );
}
