import type { TFunction } from 'i18next';
import { describe, expect, it } from 'vitest';
import type { DeskTask } from '../api/types';
import {
  deptLoadText,
  originalLineText,
  sourceText,
  triageEmptyText,
  triageSubtitleText,
  waitedText,
} from './triage-text';

const t = ((key: string, options?: Record<string, string | number>) =>
  options ? `${key}|${Object.values(options).join('|')}` : key) as unknown as TFunction;

function task(overrides: Partial<DeskTask> = {}): DeskTask {
  return {
    id: '1',
    displayNo: 'T-1',
    state: 'triage',
    answered: false,
    dept: 'housekeeping',
    type: 'amenity',
    source: 'guest',
    roomNo: '305',
    floor: 3,
    vip: false,
    title: '多一條浴巾',
    guestTyped: true,
    createdAt: '2026-10-26T06:00:00Z',
    updatedAt: '2026-10-26T06:00:00Z',
    ...overrides,
  };
}

describe('originalLineText', () => {
  it.each([
    ['en', 'triage.guestLanguages.en'],
    ['ja', 'triage.guestLanguages.ja'],
    ['ko', 'triage.guestLanguages.ko'],
    ['vi', 'triage.guestLanguages.vi'],
    ['zh-TW', 'triage.guestLanguages.zh-TW'],
  ])('語言 %s 寫語言名稱', (lang, name) => {
    const line = originalLineText(task({ guestText: { lang, original: 'towel', translated: '毛巾' } }), t);
    expect(line).toBe(`triage.originalLine|${name}|towel`);
  });

  it.each(['fr', null, undefined])('語言 %s 寫外文', (lang) => {
    const line = originalLineText(task({ guestText: { lang, original: 'serviette', translated: '毛巾' } }), t);
    expect(line).toBe('triage.originalLine|triage.guestLanguages.other|serviette');
  });

  it('沒有翻譯或沒有原文時沒有原文列', () => {
    expect(originalLineText(task({ guestText: { original: 'towel', translated: '' } }), t)).toBeNull();
    expect(originalLineText(task({ guestText: { original: '', translated: '毛巾' } }), t)).toBeNull();
    expect(originalLineText(task({ guestText: null }), t)).toBeNull();
  });
});

describe('sourceText', () => {
  it.each(['guest', 'frontdesk', 'system'] as const)('來源 %s 套進來源句', (source) => {
    expect(sourceText(task({ source } as Partial<DeskTask>), t)).toBe(`triage.source|board.card.sources.${source}`);
  });
});

describe('waitedText', () => {
  it('0 分寫不到 1 分', () => {
    expect(waitedText(0, t)).toBe('triage.waitedUnderOne');
  });

  it('1 分與 12 分寫已等 N 分', () => {
    expect(waitedText(1, t)).toBe('triage.waited|1');
    expect(waitedText(12, t)).toBe('triage.waited|12');
  });
});

describe('deptLoadText', () => {
  it('房務與工務分開寫', () => {
    expect(deptLoadText('housekeeping', 3, t)).toBe('triage.deptLoad.housekeeping|3');
    expect(deptLoadText('engineering', 0, t)).toBe('triage.deptLoad.engineering|0');
  });
});

describe('triageSubtitleText', () => {
  const now = new Date('2026-10-26T06:30:00Z');

  it('有單時寫張數與最久已等分鐘', () => {
    const rows = [
      task({ id: 'a', createdAt: '2026-10-26T06:25:00Z' }),
      task({ id: 'b', createdAt: '2026-10-26T06:18:00Z' }),
    ];
    expect(triageSubtitleText(rows, now, t)).toBe('triage.subtitle|2|12');
  });

  it('只有 1 張時張數是 1', () => {
    expect(triageSubtitleText([task({ createdAt: '2026-10-26T06:30:00Z' })], now, t)).toBe('triage.subtitle|1|0');
  });

  it('清單是空的時候寫沒有要派的單，不需要現在', () => {
    expect(triageSubtitleText([], now, t)).toBe('triage.empty.subtitle');
    expect(triageSubtitleText([], undefined, t)).toBe('triage.empty.subtitle');
  });

  it('資料還沒讀到、或有單但還沒有現在時不寫字', () => {
    expect(triageSubtitleText(undefined, now, t)).toBe('');
    expect(triageSubtitleText([task()], undefined, t)).toBe('');
  });
});

describe('triageEmptyText', () => {
  it('今天派過單也回答過時是都派完了，說明帶兩個張數', () => {
    expect(triageEmptyText({ assignedCount: 2, answeredCount: 1 }, t)).toEqual({
      done: true,
      title: 'triage.empty.doneTitle',
      hint: 'triage.empty.doneHint|2|1',
    });
  });

  it('只當場回答過、沒派過單也是都派完了', () => {
    expect(triageEmptyText({ assignedCount: 0, answeredCount: 1 }, t)).toEqual({
      done: true,
      title: 'triage.empty.doneTitle',
      hint: 'triage.empty.doneHint|0|1',
    });
  });

  it('兩個都是 0 時是目前沒有要派的單', () => {
    expect(triageEmptyText({ assignedCount: 0, answeredCount: 0 }, t)).toEqual({
      done: false,
      title: 'triage.empty.noneTitle',
      hint: 'triage.empty.noneHint',
    });
  });
});
