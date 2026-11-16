/** 四種介面語言，順序就是語言選單由上到下的順序。 */
export const LANGUAGES = ['zh-TW', 'en', 'ja', 'vi'] as const;

export type Language = (typeof LANGUAGES)[number];

/** 還沒有任何依據可以決定語言時用繁體中文；某個鍵在其他語言缺翻譯時也退回它。 */
export const DEFAULT_LANGUAGE: Language = 'zh-TW';

export function isLanguage(value: unknown): value is Language {
  return (LANGUAGES as readonly unknown[]).includes(value);
}
