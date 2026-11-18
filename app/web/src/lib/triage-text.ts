import type { TFunction } from 'i18next';
import type { DeskMyToday, DeskTask } from '../api/types';
import { waitingMinutes } from './wait-pill';

/**
 * 待派單頁各處的文字組法：原文列、來源、已等時間、頁首張數、頁副標。
 * 寫成不碰畫面的純函式，畫面文字由呼叫端傳進來的 `t` 轉成當前語言。
 */

/** 原文列寫得出名稱的住客語言（單上的語言代碼）；其他語言寫「外文」。 */
const GUEST_LANGUAGES = ['zh-TW', 'en', 'ja', 'ko', 'vi'] as const;

function isGuestLanguage(lang: string | null | undefined): lang is (typeof GUEST_LANGUAGES)[number] {
  return (GUEST_LANGUAGES as readonly (string | null | undefined)[]).includes(lang);
}

/** 原文列「{語言}原文：…」；單上沒有翻譯或沒有原文時沒有這一列，回 null。 */
export function originalLineText(task: DeskTask, t: TFunction): string | null {
  const translated = task.guestText?.translated;
  const original = task.guestText?.original;
  if (!translated || !original) {
    return null;
  }
  const lang = task.guestText?.lang;
  return t('triage.originalLine', {
    language: isGuestLanguage(lang) ? t(`triage.guestLanguages.${lang}`) : t('triage.guestLanguages.other'),
    text: original,
  });
}

/** 來源文字，例如「· 住客掃碼 ·」。 */
export function sourceText(task: DeskTask, t: TFunction): string {
  return t('triage.source', { source: t(`board.card.sources.${task.source}`) });
}

/** 已等文字：不到 1 分寫「不到 1 分」，其餘寫「已等 N 分」。 */
export function waitedText(minutes: number, t: TFunction): string {
  return minutes < 1 ? t('triage.waitedUnderOne') : t('triage.waited', { minutes });
}

/** 頁首某個部門的待接張數，房務、工務各一句。 */
export function deptLoadText(dept: 'housekeeping' | 'engineering', count: number, t: TFunction): string {
  return t(`triage.deptLoad.${dept}`, { count });
}

/**
 * 頁副標「{N} 張等你派單 · 最久已等 {M} 分」；清單是空的時候寫「目前沒有要派的單」。
 * 資料還沒讀到（傳 undefined）、或有單但還沒有校正後的現在時，不寫字，回空字串。
 */
export function triageSubtitleText(rows: readonly DeskTask[] | undefined, now: Date | undefined, t: TFunction): string {
  if (!rows) {
    return '';
  }
  if (rows.length === 0) {
    return t('triage.empty.subtitle');
  }
  if (!now) {
    return '';
  }
  return t('triage.subtitle', {
    count: rows.length,
    minutes: Math.max(...rows.map((task) => waitingMinutes(task, now) ?? 0)),
  });
}

/** 待派單清單是空的時候要寫的標題與說明，以及是哪一種空狀態。 */
export interface TriageEmptyText {
  /** 今天派過單或當場回答過是 true（「都派完了」），兩個都沒有是 false（「目前沒有要派的單」）。 */
  done: boolean;
  title: string;
  hint: string;
}

/**
 * 兩種空狀態的字。張數照後端給的登入者今天的張數寫，被復原的派單後端已經扣掉。
 * 派單或當場回答只要有一個不是 0，就是「都派完了」。
 */
export function triageEmptyText(myToday: DeskMyToday, t: TFunction): TriageEmptyText {
  const done = myToday.assignedCount + myToday.answeredCount > 0;
  if (!done) {
    return { done, title: t('triage.empty.noneTitle'), hint: t('triage.empty.noneHint') };
  }
  return {
    done,
    title: t('triage.empty.doneTitle'),
    hint: t('triage.empty.doneHint', { assigned: myToday.assignedCount, answered: myToday.answeredCount }),
  };
}
