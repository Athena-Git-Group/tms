import type { i18n as I18n } from 'i18next';
import { matchBrowserLanguage } from '../lib/browser-language';
import { useSessionStore, type Session } from '../stores/session';
import {
  readLoginPageLanguage,
  readStaffLanguage,
  writeLoginPageLanguage,
  writeStaffLanguage,
} from './language-storage';
import { DEFAULT_LANGUAGE, isLanguage, type Language } from './languages';

/**
 * 決定畫面現在用哪一種介面語言，並在登入、登出、換語言時記到這台裝置上。
 *
 * 規則照這個順序：
 * 1. 登入前用登入頁那一筆；這台裝置還沒有人選過時跟瀏覽器的語言。
 * 2. 登入後用這個人自己那一筆。從來沒選過的人沿用登入頁當下的語言，並記成他的那一筆，
 *    下次在別的語言的登入頁登入也還是這個語言。
 * 3. 登出時把登入頁那一筆改成剛登出那一位的語言，下一位看到的登入頁維持同一種語言。
 *
 * 登入、登出都是看登入狀態有沒有換人來判斷，不靠各畫面自己呼叫。
 * 登出、登入狀態過期、改密碼後被送回登入頁走的是不同的路，各自呼叫的話漏掉一條，
 * 登入頁就會跳回上上一位的語言。
 */

/** 登入前的語言。 */
export function languageBeforeLogin(): Language {
  return readLoginPageLanguage() ?? matchBrowserLanguage(browserLanguages());
}

/**
 * 重新整理或另開分頁時已經是登入狀態，用這個人自己那一筆；沒有的話照登入前的規則。
 * 這裡只讀不寫，他真的經過登入頁登入時才記下他的語言。
 */
function languageOnStart(session: Session | null): Language {
  if (session === null) return languageBeforeLogin();
  return readStaffLanguage(session.loginId) ?? languageBeforeLogin();
}

/**
 * 在語言選單或語言頁選了一種語言：立刻套用，並記在這台裝置上。
 * 已登入時記成這個人自己的那一筆，同時把登入頁那一筆也換成它，換完沒登出就關掉分頁，
 * 下一位打開登入頁看到的也是這個語言（和雛形一致）；還沒登入時只記登入頁那一筆。
 */
export function chooseLanguage(i18n: I18n, language: Language): void {
  const session = useSessionStore.getState().session;
  if (session !== null) {
    writeStaffLanguage(session.loginId, language);
  }
  writeLoginPageLanguage(language);
  void i18n.changeLanguage(language);
}

/** 多語系初始化時用這個語言，畫面第一次畫出來就是對的語言，不會先閃一下繁體中文。 */
export function initialLanguage(): Language {
  return languageOnStart(useSessionStore.getState().session);
}

/**
 * 畫面啟動時呼叫一次，之後登入狀態每換一個人就套用上面的第 2、3 條規則。
 *
 * `<html lang>` 也在這裡跟著介面語言改。瀏覽器與螢幕報讀軟體靠它決定用哪種語言念畫面、
 * 挑哪一套字形；不改的話，日文介面的漢字會用中文字形顯示，報讀軟體也會用中文念英文字。
 */
export function followSession(i18n: I18n): void {
  syncDocumentLanguage(i18n.language);
  i18n.on('languageChanged', syncDocumentLanguage);

  useSessionStore.subscribe((state, previous) => {
    const before = previous.session?.loginId ?? null;
    const after = state.session?.loginId ?? null;
    if (before === after) return;
    const current = currentLanguage(i18n);
    if (before !== null) {
      writeLoginPageLanguage(current);
    }
    if (after !== null) {
      const own = readStaffLanguage(after);
      if (own === null) {
        writeStaffLanguage(after, current);
      } else if (own !== current) {
        void i18n.changeLanguage(own);
      }
    }
  });
}

function currentLanguage(i18n: I18n): Language {
  return isLanguage(i18n.language) ? i18n.language : DEFAULT_LANGUAGE;
}

function syncDocumentLanguage(language: string): void {
  if (typeof document !== 'undefined') document.documentElement.lang = language;
}

function browserLanguages(): readonly string[] {
  if (typeof navigator === 'undefined') return [];
  if (navigator.languages && navigator.languages.length > 0) return navigator.languages;
  return navigator.language ? [navigator.language] : [];
}
