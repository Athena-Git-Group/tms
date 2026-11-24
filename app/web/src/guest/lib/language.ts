import type { Language } from '../../i18n/languages';

const BY_PRIMARY_SUBTAG: Readonly<Record<string, Language>> = {
  zh: 'zh-TW',
  ja: 'ja',
  vi: 'vi',
};

export function guestLanguageFor(navigatorLanguages: readonly string[]): Language {
  const primary = (navigatorLanguages[0] ?? '').trim().toLowerCase().split(/[-_]/)[0] ?? '';
  return BY_PRIMARY_SUBTAG[primary] ?? 'en';
}
