import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { followSession, initialLanguage } from './interface-language';
import { DEFAULT_LANGUAGE, LANGUAGES } from './languages';
import { en } from './locales/en';
import { ja } from './locales/ja';
import { vi } from './locales/vi';
import { zhTW } from './locales/zh-TW';

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
  // React 輸出文字時已經會轉義特殊字元，i18next 再轉一次會讓「&」顯示成「&amp;」。
  interpolation: { escapeValue: false },
});

followSession(i18n);

export { i18n };
