import { describe, expect, it } from 'vitest';
import { backTargetOf, cameFrom, requestLink } from './back-target';

describe('backTargetOf', () => {
  it.each([
    ['home', '/g/abc'],
    ['list', '/g/abc/requests'],
    ['sent', '/g/abc/requests'],
    [null, '/g/abc/requests'],
  ] as const)('從 %s 進來，返回 %s', (from, target) => {
    expect(backTargetOf(from, 'abc')).toBe(target);
  });
});

describe('cameFrom', () => {
  it('認得連結帶的三種來處', () => {
    for (const from of ['home', 'list', 'sent'] as const) {
      expect(cameFrom(requestLink('abc', 't1', from).state)).toBe(from);
    }
  });

  it.each([null, undefined, 'home', {}, { from: 'guide' }])('不認得的 state 當成沒有紀錄：%j', (state) => {
    expect(cameFrom(state)).toBeNull();
  });
});

describe('requestLink', () => {
  it('網址是那一筆的進度頁，id 會編碼', () => {
    expect(requestLink('abc', 'a/b', 'list').to).toBe('/g/abc/requests/a%2Fb');
  });
});
