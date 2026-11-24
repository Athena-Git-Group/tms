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

/**
 * 住客外框掛上時決定介面語言：選過的優先，沒選過看手機瀏覽器的語言。
 * 在畫面畫出來之前就切好，住客不會先看到櫃檯那一套語言再跳成自己的。
 */
export function useGuestLanguage(): void {
  const { i18n } = useTranslation();
  useLayoutEffect(() => apply(i18n, readGuestLanguage() ?? guestLanguageFor(browserLanguages())), [i18n]);
}

/** 住客在語言選單選了一種語言：馬上換，並記在這支手機上。 */
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
