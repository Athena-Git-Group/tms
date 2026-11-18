export const LANGUAGES = ['zh-TW', 'en', 'ja', 'vi'] as const;

export type Language = (typeof LANGUAGES)[number];

export const DEFAULT_LANGUAGE: Language = 'zh-TW';

export const LANGUAGE_OWN_NAMES: Record<Language, string> = {
  'zh-TW': '繁體中文',
  en: 'English',
  ja: '日本語',
  vi: 'Tiếng Việt',
};

export function isLanguage(value: unknown): value is Language {
  return (LANGUAGES as readonly unknown[]).includes(value);
}
