import type { ParseKeys, TFunction } from 'i18next';

export interface DisplayText {
  key: ParseKeys;
  values?: Record<string, string | number | DisplayText>;
}

export function displayString(text: DisplayText, t: TFunction): string {
  const values: Record<string, string | number> = {};
  for (const [name, value] of Object.entries(text.values ?? {})) {
    values[name] = typeof value === 'object' ? displayString(value, t) : value;
  }
  // 這裡用型別轉換繞過 i18next 對每一句的型別檢查。翻譯鍵在建立 `DisplayText` 時已經檢查過，
  // 代入值則是執行時才組出來的，對不上每一句各自宣告的代入值型別。
  const translate = t as unknown as (key: string, values: Record<string, string | number>) => string;
  return translate(text.key, values);
}
