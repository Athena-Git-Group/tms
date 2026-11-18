import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { followSession, initialLanguage } from './interface-language';
import { DEFAULT_LANGUAGE, LANGUAGES } from './languages';
import { en } from './locales/en';
import { ja } from './locales/ja';
import { vi } from './locales/vi';
import { zhTW } from './locales/zh-TW';

/**
 * 多語系的初始化。四種語言的文字都打包在一起，換語言不需要再向伺服器要檔案。
 * 一開始用哪一種、之後什麼時候換，照 `interface-language.ts` 的規則。
 */
void i18n.use(initReactI18next).init({
  resources: {
    'zh-TW': { translation: zhTW },
    en: { translation: en },
    ja: { translation: ja },
    vi: { translation: vi },
  },
  lng: initialLanguage(),
  fallbackLng: DEFAULT_LANGUAGE,
  supportedLngs: LANGUAGES,
  // React 輸出文字時已經會處理特殊字元，再處理一次會把「&」之類的字變成亂碼。
  interpolation: { escapeValue: false },
});

followSession(i18n);

export { i18n };
