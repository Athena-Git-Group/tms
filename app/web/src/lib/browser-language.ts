import { DEFAULT_LANGUAGE, type Language } from '../i18n/languages';

const BY_PRIMARY_SUBTAG: Readonly<Record<string, Language>> = {
  en: 'en',
  ja: 'ja',
  vi: 'vi',
};

export function matchBrowserLanguage(browserLanguages: readonly string[]): Language {
  const first = browserLanguages[0];
  if (first === undefined) return DEFAULT_LANGUAGE;
  const primary = first.trim().toLowerCase().split(/[-_]/)[0] ?? '';
  return BY_PRIMARY_SUBTAG[primary] ?? DEFAULT_LANGUAGE;
}
