import type { TFunction } from 'i18next';
import { describe, expect, it } from 'vitest';
import type { DeskTask } from '../api/types';
import {
  ANSWER_MAX_LENGTH,
  answerLength,
  answerQuoteHead,
  clampAnswer,
  isSendableAnswer,
  sentAgoText,
  stripAnswer,
} from './answer-text';

/** 假的翻譯函式：回傳「鍵」加上參數，方便斷言用的是哪一句、帶了什麼值。 */
const t = ((key: string, options?: Record<string, string | number>) =>
  options ? `${key}|${Object.values(options).join('|')}` : key) as unknown as TFunction;

const sentAt = new Date('2026-10-26T06:00:00Z');

function task(overrides: Partial<DeskTask> = {}): DeskTask {
  return {
    id: '1',
    displayNo: 'T-1',
    state: 'triage',
    answered: false,
    dept: 'frontdesk',
    type: 'other',
    source: 'guest',
    roomNo: '305',
    floor: 3,
    vip: false,
    guestName: '陳怡安',
    title: '早餐幾點',
    guestTyped: true,
    createdAt: sentAt.toISOString(),
    updatedAt: sentAt.toISOString(),
    ...overrides,
  };
}

function minutesAfter(minutes: number): Date {
  return new Date(sentAt.getTime() + minutes * 60_000);
}

describe('sentAgoText', () => {
  it('不到 1 分寫剛剛', () => {
    expect(sentAgoText(task(), minutesAfter(0.9), t)).toBe('answer.justNow');
  });

  it('滿 1 分起寫 N 分鐘前，不足一分的部分捨去', () => {
    expect(sentAgoText(task(), minutesAfter(3.7), t)).toBe('answer.minutesAgo|3');
  });

  it('還沒有校正後的現在時當成剛剛', () => {
    expect(sentAgoText(task(), undefined, t)).toBe('answer.justNow');
  });
});

describe('answerQuoteHead', () => {
  it('房號、住客、來源、多久前用「 · 」串起來', () => {
    expect(answerQuoteHead(task(), minutesAfter(3), t)).toBe(
      '305 · 陳怡安 · board.card.sources.guest · answer.minutesAgo|3',
    );
  });

  it('空房的單沒有住客名那一段', () => {
    expect(answerQuoteHead(task({ guestName: null }), minutesAfter(3), t)).toBe(
      '305 · board.card.sources.guest · answer.minutesAgo|3',
    );
  });
});

describe('answerLength', () => {
  it('中文字一個算一個', () => {
    expect(answerLength('早餐幾點')).toBe(4);
  });

  it('表情符號（代理對）算一個字，和後端 codePointCount 相同', () => {
    expect('😀'.length).toBe(2);
    expect(answerLength('好的😀')).toBe(3);
    expect(answerLength('𠮷野家')).toBe(3);
  });
});

describe('clampAnswer', () => {
  it('120 字以內原樣留著', () => {
    const text = '字'.repeat(ANSWER_MAX_LENGTH);
    expect(clampAnswer(text)).toBe(text);
  });

  it('超過 120 字只留前 120 字', () => {
    expect(clampAnswer('字'.repeat(130))).toBe('字'.repeat(ANSWER_MAX_LENGTH));
  });

  it('全是表情符號時能留滿 120 個，不會因為 UTF-16 單位只剩 60 個', () => {
    const clamped = clampAnswer('😀'.repeat(130));
    expect(answerLength(clamped)).toBe(ANSWER_MAX_LENGTH);
    expect(clamped).toBe('😀'.repeat(ANSWER_MAX_LENGTH));
  });

  it('第 120 個字是表情符號時整個留下，不切成半個', () => {
    const clamped = clampAnswer(`${'字'.repeat(119)}😀尾`);
    expect(clamped).toBe(`${'字'.repeat(119)}😀`);
    expect(clamped.endsWith('\uD83D')).toBe(false);
  });
});

describe('stripAnswer', () => {
  it('去掉頭尾的空格、全形空格、tab 與換行，中間的空白留著', () => {
    expect(stripAnswer(' \u3000\t早餐 07:00–10:00\n  ')).toBe('早餐 07:00–10:00');
  });

  it('不換行空格和後端 strip 一樣不去掉', () => {
    expect(stripAnswer('\u00A0好的\u00A0')).toBe('\u00A0好的\u00A0');
  });

  it('U+001F 這類分隔控制字元和後端 strip 一樣會去掉', () => {
    expect(stripAnswer('\u001F好的\u001C')).toBe('好的');
  });
});

describe('isSendableAnswer', () => {
  it('空的或只有空白不能送', () => {
    expect(isSendableAnswer('')).toBe(false);
    expect(isSendableAnswer('   \n ')).toBe(false);
  });

  it('去掉頭尾空白後 1 到 120 字可以送', () => {
    expect(isSendableAnswer('  好  ')).toBe(true);
    expect(isSendableAnswer(` ${'字'.repeat(ANSWER_MAX_LENGTH)} `)).toBe(true);
  });

  it('120 個表情符號可以送，121 個不行', () => {
    expect(isSendableAnswer('😀'.repeat(ANSWER_MAX_LENGTH))).toBe(true);
    expect(isSendableAnswer('😀'.repeat(ANSWER_MAX_LENGTH + 1))).toBe(false);
  });
});
