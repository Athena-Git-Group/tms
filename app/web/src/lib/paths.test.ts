import { describe, expect, test } from 'vitest';
import { PATHS, hotelPath } from './paths';

describe('hotelPath', () => {
  test('有飯店識別代碼時接在代碼底下', () => {
    expect(hotelPath('athena', PATHS.login)).toBe('/athena/login');
    expect(hotelPath('athena', PATHS.changePassword)).toBe('/athena/change-password');
  });

  test('沒有代碼時就是那一頁本身的路徑', () => {
    expect(hotelPath(undefined, PATHS.board)).toBe('/board');
    expect(hotelPath('', PATHS.triage)).toBe('/triage');
  });

  test('代碼裡的特殊字元會被編碼，不會被讀成另一段路徑', () => {
    expect(hotelPath('a/b', PATHS.board)).toBe('/a%2Fb/board');
  });
});
