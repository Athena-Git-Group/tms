import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useSessionStore, type Session } from '../stores/session';
import { ApiError } from './errors';
import { fetchPhotoBlob } from './repair-photos';

const SESSION: Session = {
  token: 'staff-token',
  expiresAt: '2026-11-27T00:00:00Z',
  staffId: 's1',
  name: '林佩君',
  loginId: 'F0101',
  hotelName: '示範飯店',
  role: 'frontdesk',
  hotelCode: 'demo-hotel',
  mustChangePassword: false,
} as Session;

const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);

describe('fetchPhotoBlob', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
    useSessionStore.setState({ session: SESSION });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    useSessionStore.setState({ session: null });
  });

  it('以 Authorization 標頭帶登入憑證讀照片內容，網址沒有查詢字串', async () => {
    fetchMock.mockResolvedValue(new Response(JPEG, { status: 200, headers: { 'Content-Type': 'image/jpeg' } }));

    const blob = await fetchPhotoBlob('p-1');

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(String(url)).toMatch(/\/api\/v1\/repair-photos\/p-1\/content$/);
    expect(String(url)).not.toContain('?');
    expect(String(url)).not.toContain('staff-token');
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer staff-token');
    expect(blob).toBeInstanceOf(Blob);
    expect(new Uint8Array(await blob.arrayBuffer())).toEqual(JPEG);
  });

  it('後端回 404 時丟出 not_found 的 ApiError', async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ code: 'not_found', message: 'photo not found' }), {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const error = await fetchPhotoBlob('p-legacy').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(404);
    expect((error as ApiError).code).toBe('not_found');
  });

  it('請求被中止時不回傳照片', async () => {
    fetchMock.mockImplementation(
      (_url, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
        }),
    );
    const controller = new AbortController();

    const pending = fetchPhotoBlob('p-1', controller.signal);
    controller.abort();

    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
  });
});
