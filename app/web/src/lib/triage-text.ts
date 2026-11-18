import type { TFunction } from 'i18next';
import type { DeskMyToday, DeskTask } from '../api/types';
import { waitingMinutes } from './wait-pill';

const GUEST_LANGUAGES = ['zh-TW', 'en', 'ja', 'ko', 'vi'] as const;

function isGuestLanguage(lang: string | null | undefined): lang is (typeof GUEST_LANGUAGES)[number] {
  return (GUEST_LANGUAGES as readonly (string | null | undefined)[]).includes(lang);
}

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

export function sourceText(task: DeskTask, t: TFunction): string {
  return t('triage.source', { source: t(`board.card.sources.${task.source}`) });
}

export function waitedText(minutes: number, t: TFunction): string {
  return minutes < 1 ? t('triage.waitedUnderOne') : t('triage.waited', { minutes });
}

export function deptLoadText(dept: 'housekeeping' | 'engineering', count: number, t: TFunction): string {
  return t(`triage.deptLoad.${dept}`, { count });
}

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

export interface TriageEmptyText {
  done: boolean;
  title: string;
  hint: string;
}

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
