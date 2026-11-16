import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { DEFAULT_LANGUAGE, LANGUAGES } from './languages';
import { en } from './locales/en';
import { ja } from './locales/ja';
import { vi } from './locales/vi';
import { zhTW } from './locales/zh-TW';

/**
 * 多語系的初始化。四種語言的文字都打包在一起，換語言不需要再向伺服器要檔案。
 *
 * 一開始固定用繁體中文。依瀏覽器語言、登入頁上次的語言、每個人選過的語言來決定
 * 要用哪一種，是介面語言功能的事：它算出結果後呼叫 `i18n.changeLanguage(...)`。
 */
void i18n.use(initReactI18next).init({
  resources: {
    'zh-TW': { translation: zhTW },
    en: { translation: en },
    ja: { translation: ja },
    vi: { translation: vi },
  },
  lng: DEFAULT_LANGUAGE,
  fallbackLng: DEFAULT_LANGUAGE,
  supportedLngs: LANGUAGES,
  // React 輸出文字時已經會處理特殊字元，再處理一次會把「&」之類的字變成亂碼。
  interpolation: { escapeValue: false },
});

export { i18n };
