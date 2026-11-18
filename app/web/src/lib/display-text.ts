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
  // 鍵在 `DisplayText` 建立時已經檢查過；這裡的代入值是執行時才組出來的，套不上每一句各自的代入值型別。
  const translate = t as unknown as (key: string, values: Record<string, string | number>) => string;
  return translate(text.key, values);
}
