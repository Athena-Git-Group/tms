import { describe, expect, it } from 'vitest';
import { greetingIsCompact, greetingKey } from './greeting';

const at = (hm: string) => new Date(`2026-11-20T${hm}:00`);

describe('greetingKey', () => {
  it.each([
    ['06:00', 'guest.greeting.morning'],
    ['10:59', 'guest.greeting.morning'],
    ['11:00', 'guest.greeting.afternoon'],
    ['17:59', 'guest.greeting.afternoon'],
    ['18:00', 'guest.greeting.evening'],
    ['23:30', 'guest.greeting.evening'],
  ])('%s 是 %s', (hm, key) => {
    expect(greetingKey(at(hm))).toBe(key);
  });
});

describe('greetingIsCompact', () => {
  it('繁體中文的問候不用改小', () => {
    expect(greetingIsCompact(['早安', '午安', '晚安', '今天需要什麼？'])).toBe(false);
  });

  it('日文最長一行超過 10 個字寬就改小', () => {
    expect(greetingIsCompact(['おはようございます', 'こんにちは', 'こんばんは', '何をお手伝いしましょう？'])).toBe(true);
  });
});
