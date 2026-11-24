import { describe, expect, it, vi } from 'vitest';
import { ApiError, NetworkError } from '../../api/errors';
import { goToSent, resultNotice, submitSequentially } from './submit';

function keys() {
  let n = 0;
  return () => `key-${++n}`;
}

const limited = () => new ApiError(429, { code: 'rate_limited', message: '' });

describe('submitSequentially', () => {
  it('全部成功：依序一張一張送，每張一把不同的鍵', async () => {
    const log: string[] = [];
    let open = 0;
    const outcome = await submitSequentially(
      ['毛巾', '礦泉水'],
      async (item, key) => {
        open += 1;
        expect(open).toBe(1);
        log.push(`${item}:${key}`);
        await Promise.resolve();
        open -= 1;
        return { id: `id-${item}` };
      },
      keys(),
    );
    expect(log).toEqual(['毛巾:key-1', '礦泉水:key-2']);
    expect(outcome).toEqual({ kind: 'all-sent', created: ['id-毛巾', 'id-礦泉水'] });
  });

  it('第二張被限流：停下來，保留第一張，不送第三張', async () => {
    const sent: string[] = [];
    const outcome = await submitSequentially(
      ['毛巾', '礦泉水', '衣架'],
      async (item) => {
        sent.push(item);
        if (item === '礦泉水') throw limited();
        return { id: `id-${item}` };
      },
      keys(),
    );
    expect(sent).toEqual(['毛巾', '礦泉水']);
    expect(outcome).toEqual({ kind: 'stopped-by-limit', created: ['id-毛巾'] });
  });

  it.each([
    ['連不上', new NetworkError(new TypeError('Failed to fetch'))],
    ['後端出錯', new ApiError(503, { code: 'unavailable', message: '' })],
  ])('%s：算成沒送成，保留已建立的', async (_name, failure) => {
    const outcome = await submitSequentially(
      ['毛巾', '礦泉水'],
      async (item) => {
        if (item === '礦泉水') throw failure;
        return { id: `id-${item}` };
      },
      keys(),
    );
    expect(outcome).toEqual({ kind: 'stopped-by-network', created: ['id-毛巾'] });
  });
});

describe('resultNotice', () => {
  const lines = (notice: ReturnType<typeof resultNotice>) =>
    notice && [notice.tone, notice.title.key, notice.body.key, notice.title.params.sent || notice.body.params.sent];

  it('全部送出：沒有提示框', () => {
    expect(resultNotice({ kind: 'all-sent', created: ['a'] })).toBeNull();
  });

  it('送到一半被限流：暖色框說已送出幾項', () => {
    expect(lines(resultNotice({ kind: 'stopped-by-limit', created: ['a'] }))).toEqual([
      'caution',
      'guest.limit.title',
      'guest.limit.partial',
      1,
    ]);
  });

  it('第一張就被限流：暖色框請稍後再送出', () => {
    expect(lines(resultNotice({ kind: 'stopped-by-limit', created: [] }))).toEqual([
      'caution',
      'guest.limit.title',
      'guest.limit.none',
      0,
    ]);
  });

  it('送到一半連不上：紅色框標題說已送出幾項', () => {
    expect(lines(resultNotice({ kind: 'stopped-by-network', created: ['a', 'b'] }))).toEqual([
      'error',
      'guest.notSent.partialTitle',
      'guest.notSent.body',
      2,
    ]);
  });

  it('一張都沒送出就連不上：紅色框沒有送出', () => {
    expect(lines(resultNotice({ kind: 'stopped-by-network', created: [] }))).toEqual([
      'error',
      'guest.notSent.title',
      'guest.notSent.body',
      0,
    ]);
  });
});

describe('goToSent', () => {
  it('換到成功頁，網址帶著建立的需求 id', () => {
    const navigate = vi.fn();
    goToSent(navigate, 'abc', ['t1', 't2']);
    expect(navigate).toHaveBeenCalledWith('/g/abc/sent?ids=t1,t2');
  });
});
