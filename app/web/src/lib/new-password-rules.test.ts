import { describe, expect, test } from 'vitest';
import { allNewPasswordRulesPassed, checkNewPassword } from './new-password-rules';

describe('checkNewPassword', () => {
  test('四條都符合時全部打勾', () => {
    expect(checkNewPassword('Tms12345', 'Tms12345')).toEqual({
      minLength: true,
      letter: true,
      digit: true,
      match: true,
    });
  });

  test('只差長度時只有長度沒過', () => {
    expect(checkNewPassword('Tms1234', 'Tms1234')).toEqual({
      minLength: false,
      letter: true,
      digit: true,
      match: true,
    });
  });

  test('剛好 8 碼算過', () => {
    expect(checkNewPassword('Abcdefg1', 'Abcdefg1').minLength).toBe(true);
  });

  test('只缺英文字母時只有字母沒過', () => {
    expect(checkNewPassword('12345678', '12345678')).toEqual({
      minLength: true,
      letter: false,
      digit: true,
      match: true,
    });
  });

  test('只缺數字時只有數字沒過', () => {
    expect(checkNewPassword('Abcdefgh', 'Abcdefgh')).toEqual({
      minLength: true,
      letter: true,
      digit: false,
      match: true,
    });
  });

  test('兩欄不同時只有兩次輸入相同沒過', () => {
    expect(checkNewPassword('Tms12345', 'Tms12346')).toEqual({
      minLength: true,
      letter: true,
      digit: true,
      match: false,
    });
  });

  test('確認欄是空的時兩次輸入相同沒過', () => {
    expect(checkNewPassword('Tms12345', '').match).toBe(false);
  });

  test('兩欄都是空的時兩次輸入相同也不算過', () => {
    expect(checkNewPassword('', '').match).toBe(false);
  });

  test('中間有換行的密碼照樣找得到字母和數字', () => {
    // 和後端用 find() 一樣，只要出現一個就算有，換行不會讓判斷失效。
    expect(checkNewPassword('\nabcdefg\n1', '\nabcdefg\n1')).toEqual({
      minLength: true,
      letter: true,
      digit: true,
      match: true,
    });
  });

  test('全形英文字母、帶重音的字母、中文都不算英文字母', () => {
    expect(checkNewPassword('ＡＢＣ12345', 'ＡＢＣ12345').letter).toBe(false);
    expect(checkNewPassword('éàü12345', 'éàü12345').letter).toBe(false);
    expect(checkNewPassword('密碼12345678', '密碼12345678').letter).toBe(false);
  });

  test('全形數字不算數字', () => {
    expect(checkNewPassword('Abcdefg１', 'Abcdefg１').digit).toBe(false);
  });

  test('長度照字元單位算，一個表情符號算兩碼', () => {
    // 「A1」加三個表情符號：畫面上 5 個字，字元單位是 8。
    expect(checkNewPassword('A1😀😀😀', 'A1😀😀😀').minLength).toBe(true);
  });
});

describe('allNewPasswordRulesPassed', () => {
  test('四條都過才算全過', () => {
    expect(allNewPasswordRulesPassed(checkNewPassword('Tms12345', 'Tms12345'))).toBe(true);
  });

  test('任一條沒過就不算全過', () => {
    expect(allNewPasswordRulesPassed(checkNewPassword('Tms12345', 'Tms1234'))).toBe(false);
    expect(allNewPasswordRulesPassed(checkNewPassword('Tms1234', 'Tms1234'))).toBe(false);
  });
});
