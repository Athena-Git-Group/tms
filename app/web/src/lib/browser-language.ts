import { DEFAULT_LANGUAGE, type Language } from '../i18n/languages';

/**
 * 瀏覽器語言比對：這台裝置還沒有人選過介面語言時，從瀏覽器的語言設定挑出要用四種介面語言的哪一種。
 * 寫成不碰瀏覽器的純計算，是為了能用單元測試逐一驗各種語系代碼。
 */

/** 英文、日文、越南文不分地區都認得；中文只有繁體中文，所以其他中文和認不得的語言一樣用繁體中文。 */
const BY_PRIMARY_SUBTAG: Readonly<Record<string, Language>> = {
  en: 'en',
  ja: 'ja',
  vi: 'vi',
};

/**
 * 依瀏覽器的語言設定決定介面語言。
 * `browserLanguages` 是瀏覽器回報的語系代碼（例如 `en-US`、`ja-JP`），照使用者偏好由前到後排，
 * 也就是 `navigator.languages` 的內容；只有一個語系時傳只有一項的陣列。
 *
 * 只看排第一的那一個，也就是瀏覽器本身的語言；它不在四種之內就用繁體中文，不往後找次要的偏好。
 * 往後找的話，一台設成韓文、順帶加了英文的瀏覽器會變成英文介面，和「不在四種之內就用繁體中文」不符。
 */
export function matchBrowserLanguage(browserLanguages: readonly string[]): Language {
  const first = browserLanguages[0];
  if (first === undefined) return DEFAULT_LANGUAGE;
  const primary = first.trim().toLowerCase().split(/[-_]/)[0] ?? '';
  return BY_PRIMARY_SUBTAG[primary] ?? DEFAULT_LANGUAGE;
}
