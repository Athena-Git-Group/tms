import type { TFunction } from 'i18next';
import { describe, expect, it } from 'vitest';
import type { DeskTask, TaskState } from '../api/types';
import { cardSubtitle, cardTitle } from './card-text';

/** 假的翻譯函式：回傳「鍵」加上參數，方便斷言用的是哪一句、帶了什麼值。 */
const t = ((key: string, options?: Record<string, string>) =>
  options ? `${key}|${Object.values(options).join('|')}` : key) as unknown as TFunction;

function task(state: TaskState, overrides: Partial<DeskTask> = {}): DeskTask {
  return {
    id: '1',
    displayNo: 'T-1',
    state,
    answered: false,
    dept: 'housekeeping',
    type: 'amenity',
    source: 'guest',
    roomNo: '305',
    floor: 3,
    vip: false,
    title: '多一條浴巾',
    guestTyped: false,
    createdAt: '2026-10-26T06:00:00Z',
    updatedAt: '2026-10-26T06:00:00Z',
    ...overrides,
  };
}

describe('cardTitle', () => {
  it('非住客打字的需求照 task.title', () => {
    expect(cardTitle(task('doing'), t)).toBe('多一條浴巾');
  });

  it('住客打字且有翻譯：用翻譯並以引號包起', () => {
    const typed = task('triage', { guestTyped: true, guestText: { original: 'towel', translated: '毛巾' } });
    expect(cardTitle(typed, t)).toBe('board.card.guestQuote|毛巾');
  });

  it('住客打字沒有翻譯：用原文', () => {
    const typed = task('triage', { guestTyped: true, guestText: { original: 'towel', translated: null } });
    expect(cardTitle(typed, t)).toBe('board.card.guestQuote|towel');
  });

  it('翻譯是空字串：也退回原文', () => {
    const typed = task('triage', { guestTyped: true, guestText: { original: 'towel', translated: '' } });
    expect(cardTitle(typed, t)).toBe('board.card.guestQuote|towel');
  });
});

describe('cardSubtitle', () => {
  it('待派單沒有翻譯：只有「手動派單」', () => {
    expect(cardSubtitle(task('triage'), t)).toBe('board.card.manualDispatch');
  });

  it('待派單有翻譯：加上「已翻譯」', () => {
    const translated = task('triage', { guestText: { original: 'towel', translated: '毛巾' } });
    expect(cardSubtitle(translated, t)).toBe('board.card.manualDispatch · board.card.translated');
  });

  it('中斷是本人：顯示中斷原因', () => {
    const mine = task('interrupted', { interruption: { bySelf: true, reason: '電梯故障', handoverNote: '交接' } });
    expect(cardSubtitle(mine, t)).toBe('電梯故障');
  });

  it('中斷不是本人：顯示交接說明；沒交代時為空', () => {
    const other = task('interrupted', { interruption: { bySelf: false, reason: '電梯故障', handoverNote: '交接' } });
    expect(cardSubtitle(other, t)).toBe('交接');
    const silent = task('interrupted', { interruption: { bySelf: false, reason: '電梯故障' } });
    expect(cardSubtitle(silent, t)).toBe('');
  });

  it('延後是本人：帶姓名與原因', () => {
    const mine = task('deferred', { deferral: { bySelf: true, byName: '小美', reason: '等備品' } });
    expect(cardSubtitle(mine, t)).toBe('board.card.deferredBySelf|小美|等備品');
  });

  it('延後是主管：只帶姓名', () => {
    const boss = task('deferred', { deferral: { bySelf: false, byName: '王經理', reason: '不顯示' } });
    expect(cardSubtitle(boss, t)).toBe('board.card.deferredBy|王經理');
  });

  it('延後但沒有延後資料：回空字串，不出現開頭空格的「 延後」', () => {
    expect(cardSubtitle(task('deferred', { deferral: null }), t)).toBe('');
  });

  it('已當場回答', () => {
    expect(cardSubtitle(task('doing', { answered: true }), t)).toBe('board.card.answered');
  });

  it('已完成的打掃單：等主管查房；非打掃單不顯示', () => {
    expect(cardSubtitle(task('done', { type: 'cleaning' }), t)).toBe('board.card.awaitingInspection');
    expect(cardSubtitle(task('done', { type: 'amenity' }), t)).toBe('');
  });

  it('其他情形為空', () => {
    expect(cardSubtitle(task('doing'), t)).toBe('');
  });

  it('同時符合多種時依優先順序：中斷勝過延後資料、延後勝過已回答、已回答勝過等查房', () => {
    const both = task('interrupted', {
      interruption: { bySelf: true, reason: '電梯故障' },
      deferral: { bySelf: true, byName: '小美', reason: '等備品' },
    });
    expect(cardSubtitle(both, t)).toBe('電梯故障');
    const deferredAnswered = task('deferred', { answered: true, deferral: { bySelf: false, byName: '王經理' } });
    expect(cardSubtitle(deferredAnswered, t)).toBe('board.card.deferredBy|王經理');
    const answeredDone = task('done', { type: 'cleaning', answered: true });
    expect(cardSubtitle(answeredDone, t)).toBe('board.card.answered');
  });
});

describe('VIP 標籤的文字', () => {
  it('四份資源檔都有 board.card.vip', async () => {
    const files = await Promise.all([
      import('../i18n/locales/zh-TW'),
      import('../i18n/locales/en'),
      import('../i18n/locales/ja'),
      import('../i18n/locales/vi'),
    ]);
    for (const file of files) {
      const locale = Object.values(file)[0] as { board: { card: { vip: string } } };
      expect(locale.board.card.vip).toBe('VIP');
    }
  });
});
