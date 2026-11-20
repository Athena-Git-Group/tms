import type { Language } from '../../i18n/languages';

const BY_PRIMARY_SUBTAG: Readonly<Record<string, Language>> = {
  zh: 'zh-TW',
  ja: 'ja',
  vi: 'vi',
};

/**
 * 住客第一次打開時的介面語言，看手機瀏覽器的第一個語言：中文的任何變體一律繁體中文，日文與越南文各自對應，
 * 其餘一律英文。櫃檯不認得的語言退回繁體中文，住客退回英文，因為住客多半是外地旅客，兩邊不共用這個比對。
 */
export function guestLanguageFor(navigatorLanguages: readonly string[]): Language {
  const primary = (navigatorLanguages[0] ?? '').trim().toLowerCase().split(/[-_]/)[0] ?? '';
  return BY_PRIMARY_SUBTAG[primary] ?? 'en';
}
