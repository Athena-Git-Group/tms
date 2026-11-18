import type { TFunction } from 'i18next';
import type { DeskTask } from '../api/types';

/**
 * 看板卡片的標題與副標：依單的內容算出要顯示的字。
 * 寫成不碰畫面的純函式，畫面文字由呼叫端傳進來的 `t` 轉成當前語言，方便用單元測試逐一驗每種情形。
 */

/** 卡片標題：住客打字的需求用「」包起來，單上有翻譯時用翻譯後的文字，翻譯是空的或沒有就退回住客打的原文。 */
export function cardTitle(task: DeskTask, t: TFunction): string {
  if (!task.guestTyped) {
    return task.title;
  }
  const text = task.guestText?.translated || task.guestText?.original || task.title;
  return t('board.card.guestQuote', { text });
}

/**
 * 卡片副標，只有五種情形有字，其他回空字串。同時符合多種時依下列順序取第一個：
 * 待派單、中斷、延後中、已當場回答、已完成的打掃單（等查房）。
 */
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
