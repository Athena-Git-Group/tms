import { describe, expect, test } from 'vitest';
import { PATHS } from './paths';
import { canChangeSettings, decideRoute, sessionAtHotel } from './route-access';

const athena = { hotelCode: 'athena', role: 'frontdesk', mustChangePassword: false } as const;

describe('sessionAtHotel', () => {
  test('登入的是網址上這一家時照用', () => {
    expect(sessionAtHotel(athena, 'athena')).toBe(athena);
  });

  test('登入的是別家時當作沒有登入', () => {
    expect(sessionAtHotel(athena, 'athena2')).toBeNull();
  });

  test('代碼要一字不差，大小寫不同就是別家', () => {
    expect(sessionAtHotel(athena, 'Athena')).toBeNull();
  });

  test('沒有登入時還是沒有登入', () => {
    expect(sessionAtHotel(null, 'athena')).toBeNull();
  });

  test('登入別家的人打開這一家的看板，會被帶去這一家的登入頁，登入頁可以進', () => {
    expect(decideRoute('shell', sessionAtHotel(athena, 'athena2'))).toEqual({
      allow: false,
      redirectTo: PATHS.login,
    });
    expect(decideRoute('login', sessionAtHotel(athena, 'athena2'))).toEqual({ allow: true });
  });
});

describe('設定頁只給主管與值班主管', () => {
  const session = (role: 'frontdesk' | 'manager' | 'duty_manager') => ({ role, mustChangePassword: false });

  it.each(['manager', 'duty_manager'] as const)('%s 可以打開設定頁，也看得到側欄的設定', (role) => {
    expect(decideRoute('settings', session(role))).toEqual({ allow: true });
    expect(canChangeSettings(role)).toBe(true);
  });

  it('櫃檯直接打設定頁的網址會被帶回總覽看板，側欄也沒有設定', () => {
    expect(decideRoute('settings', session('frontdesk'))).toEqual({ allow: false, redirectTo: PATHS.board });
    expect(canChangeSettings('frontdesk')).toBe(false);
  });

  it('沒有登入時先去登入頁，還在用臨時密碼時先去設定新密碼', () => {
    expect(decideRoute('settings', null)).toEqual({ allow: false, redirectTo: PATHS.login });
    expect(decideRoute('settings', { role: 'manager', mustChangePassword: true })).toEqual({
      allow: false,
      redirectTo: PATHS.changePassword,
    });
  });
});
