import type { TFunction } from 'i18next';
import type { DeskTask } from '../api/types';

export function cardTitle(task: DeskTask, t: TFunction): string {
  if (!task.guestTyped) {
    return task.title;
  }
  const text = task.guestText?.translated || task.guestText?.original || task.title;
  return t('board.card.guestQuote', { text });
}

export function cardSubtitle(task: DeskTask, t: TFunction): string {
  if (task.state === 'triage') {
    const manual = t('board.card.manualDispatch');
    return task.guestText?.translated ? `${manual} · ${t('board.card.translated')}` : manual;
  }
  if (task.state === 'interrupted') {
    const interruption = task.interruption;
    return (interruption?.bySelf ? interruption.reason : interruption?.handoverNote) ?? '';
  }
  if (task.state === 'deferred') {
    const deferral = task.deferral;
    if (!deferral) {
      return '';
    }
    const name = deferral.byName ?? '';
    if (deferral.bySelf) {
      return t('board.card.deferredBySelf', { name, reason: deferral.reason ?? '' });
    }
    return t('board.card.deferredBy', { name });
  }
  if (task.answered) {
    return t('board.card.answered');
  }
  if (task.state === 'done' && task.type === 'cleaning') {
    return t('board.card.awaitingInspection');
  }
  return '';
}
