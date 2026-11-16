import type { Language } from '../i18n/languages';

/**
 * 瀏覽器語言比對：這台裝置還沒有人選過介面語言時，從瀏覽器的語言設定挑出要用四種介面語言的哪一種。
 * 寫成不碰瀏覽器的純計算，是為了能用單元測試逐一驗各種語系代碼。
 *
 * 這個檔目前只有函式的外形，比對的規則還沒有寫，呼叫函式會丟出 `not implemented`。
 */

/**
 * 依瀏覽器的語言設定決定介面語言。
 * `browserLanguages` 是瀏覽器回報的語系代碼（例如 `en-US`、`ja-JP`），照使用者偏好由前到後排，
 * 也就是 `navigator.languages` 的內容；只有一個語系時傳只有一項的陣列。
 */
export function matchBrowserLanguage(_browserLanguages: readonly string[]): Language {
  throw new Error('not implemented: lib/browser-language.ts matchBrowserLanguage');
}
