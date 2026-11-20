import { describe, expect, it } from 'vitest';
import { guestLanguageFor } from './language';

describe('guestLanguageFor', () => {
  it.each([
    ['zh-TW', 'zh-TW'],
    ['zh-CN', 'zh-TW'],
    ['zh-HK', 'zh-TW'],
    ['ja', 'ja'],
    ['ja-JP', 'ja'],
    ['vi', 'vi'],
    ['en-US', 'en'],
    ['ko-KR', 'en'],
  ])('%s → %s', (browser, expected) => {
    expect(guestLanguageFor([browser])).toBe(expected);
  });

  it('只看第一個語言', () => {
    expect(guestLanguageFor(['ko-KR', 'zh-TW'])).toBe('en');
  });

  it('瀏覽器沒給語言時是英文', () => {
    expect(guestLanguageFor([])).toBe('en');
  });
});
