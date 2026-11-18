import 'i18next';
import type { zhTW } from './locales/zh-TW';

// 讓 `t('...')` 的鍵對著繁體中文的資源檔檢查，打錯鍵在編譯時就會被擋下來。
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation';
    resources: { translation: typeof zhTW };
  }
}
