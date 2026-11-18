import { describe, expect, test } from 'vitest';
import { PATHS } from './paths';
import { decideRoute, sessionAtHotel } from './route-access';

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
