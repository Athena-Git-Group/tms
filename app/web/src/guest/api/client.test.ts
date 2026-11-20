import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/errors';
import {
  cancelRequest,
  createRequest,
  listRequests,
  rateRequest,
  readHome,
  scan,
  setUnauthorizedHandler,
} from './client';

interface Captured {
  url: string;
  method: string | undefined;
  headers: Record<string, string>;
  body: unknown;
}

let calls: Captured[];
let respond: () => Response;

beforeEach(() => {
  calls = [];
  respond = () => new Response('[]', { status: 200 });
  vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
    calls.push({
      url,
      method: init.method,
      headers: init.headers as Record<string, string>,
      body: init.body === undefined ? undefined : JSON.parse(init.body as string),
    });
    return respond();
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
  setUnauthorizedHandler(null);
});

describe('住客呼叫後端', () => {
  it('換 session 是公開的：路徑前綴 /guest，不帶任何憑證', async () => {
    respond = () => new Response('{}', { status: 200 });
    await scan('qr-abc');
    expect(calls[0]).toMatchObject({ url: '/guest/scan', method: 'POST', body: { token: 'qr-abc' } });
    expect(calls[0]!.headers).not.toHaveProperty('X-Guest-Session');
    expect(calls[0]!.headers).not.toHaveProperty('Authorization');
  });

  it('讀取帶 X-Guest-Session，絕不帶 Authorization', async () => {
    await readHome('gs_1');
    await listRequests('gs_1');
    expect(calls.map((call) => call.url)).toEqual(['/guest/home', '/guest/requests']);
    for (const call of calls) {
      expect(call.method).toBe('GET');
      expect(call.headers['X-Guest-Session']).toBe('gs_1');
      expect(call.headers).not.toHaveProperty('Authorization');
      expect(call.headers).not.toHaveProperty('Idempotency-Key');
    }
  });

  it('寫入都帶冪等鍵', async () => {
    respond = () => new Response('{}', { status: 200 });
    await createRequest('gs_1', { itemId: '毛巾', quantity: 2 }, 'key-1');
    await cancelRequest('gs_1', 'task-1', 'key-2');
    await rateRequest('gs_1', 'task-1', 5, 'key-3');
    expect(calls.map((call) => [call.method, call.url, call.headers['Idempotency-Key'], call.body])).toEqual([
      ['POST', '/guest/requests', 'key-1', { itemId: '毛巾', quantity: 2 }],
      ['POST', '/guest/requests/task-1/cancel', 'key-2', undefined],
      ['POST', '/guest/requests/task-1/rating', 'key-3', { rating: 5 }],
    ]);
    for (const call of calls) {
      expect(call.headers['X-Guest-Session']).toBe('gs_1');
      expect(call.headers).not.toHaveProperty('Authorization');
    }
  });

  it('帶 session 的呼叫被回 401 時換一把新的 session 重發一次', async () => {
    const statuses = [401, 200];
    respond = () => new Response('[]', { status: statuses.shift() ?? 200 });
    const rejected: string[] = [];
    setUnauthorizedHandler(async (session) => {
      rejected.push(session);
      return 'gs_new';
    });
    await expect(listRequests('gs_old')).resolves.toEqual([]);
    expect(rejected).toEqual(['gs_old']);
    expect(calls.map((call) => call.headers['X-Guest-Session'])).toEqual(['gs_old', 'gs_new']);
  });

  it('換不到 session 時丟出換 session 的錯誤，不重發', async () => {
    respond = () => new Response(JSON.stringify({ code: 'unauthorized', message: 'x' }), { status: 401 });
    const vacant = new ApiError(409, { code: 'room_vacant', message: 'x' });
    setUnauthorizedHandler(() => Promise.reject(vacant));
    await expect(listRequests('gs_old')).rejects.toBe(vacant);
    expect(calls).toHaveLength(1);
  });

  it('重發之後又是 401 就丟出，不再重發第三次', async () => {
    respond = () => new Response(JSON.stringify({ code: 'unauthorized', message: 'x' }), { status: 401 });
    const rejected: string[] = [];
    setUnauthorizedHandler(async (session) => {
      rejected.push(session);
      return 'gs_new';
    });
    await expect(listRequests('gs_old')).rejects.toMatchObject({ status: 401 });
    expect(rejected).toEqual(['gs_old', 'gs_new']);
    expect(calls).toHaveLength(2);
  });

  it('其他錯誤不換 session', async () => {
    respond = () => new Response(JSON.stringify({ code: 'rate_limited', message: 'x' }), { status: 429 });
    const rejected: string[] = [];
    setUnauthorizedHandler(async (session) => {
      rejected.push(session);
      return 'gs_new';
    });
    await expect(createRequest('gs_1', { text: '冷氣不冷', lang: 'zh-TW' }, 'k')).rejects.toMatchObject({
      status: 429,
      code: 'rate_limited',
    });
    expect(rejected).toEqual([]);
  });
});
