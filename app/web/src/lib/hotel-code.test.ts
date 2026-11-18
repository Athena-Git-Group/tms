import { describe, expect, test } from 'vitest';
import { isWellFormedHotelCode, normalizeHotelCode } from './hotel-code';

describe('normalizeHotelCode', () => {
  test('去掉前後空白並轉成小寫', () => {
    expect(normalizeHotelCode('  Athena2 ')).toBe('athena2');
  });
});

describe('isWellFormedHotelCode', () => {
  test('合格的代碼', () => {
    expect(isWellFormedHotelCode('athena')).toBe(true);
    expect(isWellFormedHotelCode('demo-hotel-2')).toBe(true);
    expect(isWellFormedHotelCode('a'.repeat(32))).toBe(true);
  });

  test('長度不在 3 到 32 之間', () => {
    expect(isWellFormedHotelCode('ab')).toBe(false);
    expect(isWellFormedHotelCode('a'.repeat(33))).toBe(false);
  });

  test('格式不對：數字開頭、大寫、連字號結尾、連用兩個連字號、中間有空白或斜線', () => {
    for (const code of ['2athena', 'Athena', 'athena-', 'demo--hotel', 'ath ena', 'ath/ena']) {
      expect(isWellFormedHotelCode(code)).toBe(false);
    }
  });

  test('網址上已有用途的保留字不能當飯店代碼', () => {
    for (const code of ['login', 'board', 'triage', 'change-password', 'api']) {
      expect(isWellFormedHotelCode(code)).toBe(false);
    }
  });
});
