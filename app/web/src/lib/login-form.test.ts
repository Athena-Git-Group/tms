import { describe, expect, test } from 'vitest';
import { LOGIN_ID_MAX_LENGTH, canSubmitLogin, normalizeLoginId } from './login-form';

describe('normalizeLoginId', () => {
  test('大小寫照打的樣子留著，不轉換', () => {
    expect(normalizeLoginId('f0101')).toBe('f0101');
    expect(normalizeLoginId('Mg1')).toBe('Mg1');
  });

  test('前後空白不去掉', () => {
    expect(normalizeLoginId(' mg1 ')).toBe(' mg1 ');
  });

  test('超過 12 個字時只留前 12 個字', () => {
    const normalized = normalizeLoginId('abcdefghijklmn');
    expect(normalized).toBe('abcdefghijkl');
    expect(normalized).toHaveLength(LOGIN_ID_MAX_LENGTH);
  });
});

describe('canSubmitLogin', () => {
  test('兩欄都有內容、也不是送出中時可以按', () => {
    expect(canSubmitLogin({ loginId: 'F0101', password: 'Tms12345', submitting: false })).toBe(true);
  });

  test('員工編號只有空白也算有內容，跟員工 App 一樣可以按', () => {
    expect(canSubmitLogin({ loginId: '   ', password: 'Tms12345', submitting: false })).toBe(true);
  });

  test('任一欄是空的時不能按', () => {
    expect(canSubmitLogin({ loginId: '', password: 'Tms12345', submitting: false })).toBe(false);
    expect(canSubmitLogin({ loginId: 'F0101', password: '', submitting: false })).toBe(false);
  });

  test('送出中時不能按', () => {
    expect(canSubmitLogin({ loginId: 'F0101', password: 'Tms12345', submitting: true })).toBe(false);
  });
});
