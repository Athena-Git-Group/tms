import { describe, expect, it } from 'vitest';
import { matchBrowserLanguage } from './browser-language';

describe('matchBrowserLanguage', () => {
  it.each([
    ['en-US', 'en'],
    ['en-GB', 'en'],
    ['ja-JP', 'ja'],
    ['vi-VN', 'vi'],
  ])('%s 對到 %s', (code, expected) => {
    expect(matchBrowserLanguage([code])).toBe(expected);
  });

  it.each(['zh-TW', 'zh-HK', 'zh-CN', 'zh', 'ko-KR', 'fr-FR'])('%s 用繁體中文', (code) => {
    expect(matchBrowserLanguage([code])).toBe('zh-TW');
  });

  it('空清單用繁體中文', () => {
    expect(matchBrowserLanguage([])).toBe('zh-TW');
  });

  it('大小寫不影響', () => {
    expect(matchBrowserLanguage(['EN-us'])).toBe('en');
    expect(matchBrowserLanguage(['JA'])).toBe('ja');
  });

  it('只有語言碼也認得', () => {
    expect(matchBrowserLanguage(['ja'])).toBe('ja');
    expect(matchBrowserLanguage(['vi'])).toBe('vi');
  });

  it('底線分隔與前後空白也認得', () => {
    expect(matchBrowserLanguage([' en_US '])).toBe('en');
  });

  it('空字串用繁體中文', () => {
    expect(matchBrowserLanguage([''])).toBe('zh-TW');
  });

  it('只看排第一的語言，不往後找次要偏好', () => {
    expect(matchBrowserLanguage(['ko-KR', 'en-US'])).toBe('zh-TW');
    expect(matchBrowserLanguage(['zh-CN', 'ja-JP'])).toBe('zh-TW');
    expect(matchBrowserLanguage(['ja-JP', 'en-US'])).toBe('ja');
  });
});
