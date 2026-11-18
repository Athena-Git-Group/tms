import type { ParseKeys, TFunction } from 'i18next';

/**
 * 「一段要顯示的文字」在純計算裡的寫法：只記是哪一句、句子裡要代入什麼，不是翻好的字。
 *
 * 這樣算文字的函式不必碰多語系，換語言時也不用重算；畫面拿到後用 `t(text.key, text.values)` 轉成字。
 * `key` 對著繁體中文資源檔（`i18n/locales/zh-TW.ts`）檢查，資源檔沒有的鍵在編譯時就會被擋下來。
 * 同仁與住客輸入的文字、姓名、房號這類不翻譯的內容放在 `values` 原樣代入。
 *
 * `values` 的值也可以是另一段 `DisplayText`，用在句子裡嵌著另一段要翻譯的字的時候，
 * 例如「派給{部門}」的部門名稱。算文字的函式拿不到翻譯函式，只能回「嵌的是哪一句」；
 * 畫面要先把裡面那一段轉成字，再代入外面這一句。
 */
export interface DisplayText {
  key: ParseKeys;
  values?: Record<string, string | number | DisplayText>;
}

/**
 * 把一段 `DisplayText` 轉成目前語言的字。`values` 裡嵌著的 `DisplayText` 先轉成字再代入外面這一句。
 * `t` 由畫面傳進來（`useTranslation` 拿到的那一個），這個檔本身不碰多語系的設定。
 */
export function displayString(text: DisplayText, t: TFunction): string {
  const values: Record<string, string | number> = {};
  for (const [name, value] of Object.entries(text.values ?? {})) {
    values[name] = typeof value === 'object' ? displayString(value, t) : value;
  }
  // 鍵在 `DisplayText` 建立時已經檢查過；這裡的代入值是執行時才組出來的，套不上每一句各自的代入值型別。
  const translate = t as unknown as (key: string, values: Record<string, string | number>) => string;
  return translate(text.key, values);
}
