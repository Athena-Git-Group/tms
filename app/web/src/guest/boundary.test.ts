import { describe, expect, it } from 'vitest';

const sources = import.meta.glob(['./**/*.{ts,tsx}', '!./boundary.test.ts'], {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

const FORBIDDEN = [
  { what: '櫃檯的登入狀態 stores/session', pattern: /from\s+['"][^'"]*\/stores\/session['"]/ },
  { what: '櫃檯的權限判斷 lib/route-access', pattern: /from\s+['"][^'"]*\/lib\/route-access['"]/ },
  { what: '帶員工憑證的 authHeaders', pattern: /\bauthHeaders\b/ },
];

describe('住客的程式和櫃檯的登入狀態分開', () => {
  it('掃得到住客的程式', () => {
    expect(Object.keys(sources).length).toBeGreaterThan(10);
  });

  it.each(FORBIDDEN)('沒有任何住客的檔用到$what', ({ pattern }) => {
    const offenders = Object.entries(sources)
      .filter(([, text]) => pattern.test(text))
      .map(([file]) => file);
    expect(offenders).toEqual([]);
  });
});
