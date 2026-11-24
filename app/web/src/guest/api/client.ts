import { ApiError, backendUrl, fetchJson } from '../../api/errors';
import type { CreateRequestBody, GuestHome, GuestTask, RatingBody, ScanResponse } from './types';

/**
 * 住客自己的一層呼叫：路徑前綴 `/guest`，session 放 `X-Guest-Session`。
 * 絕不帶 `Authorization`，那是櫃檯登入用的標頭；兩種憑證不混在同一個標頭名下，同一支手機上兩邊才互不影響。
 */

const GUEST_PREFIX = '/guest';

export const GUEST_SESSION_HEADER = 'X-Guest-Session';

export interface GuestSendOptions {
  method: 'GET' | 'POST';
  body?: unknown;
  sessionToken?: string;
  idempotencyKey?: string;
  signal?: AbortSignal;
}

let onUnauthorized: ((rejectedSession: string) => Promise<string>) | null = null;

/**
 * 帶著 session 的呼叫被回 401 時交給這裡換一把新的 session，拿到就用它重發一次；
 * 換不到時這裡丟出的錯誤就是這次呼叫的錯誤。由進入層用網址上的代碼重換。
 */
export function setUnauthorizedHandler(handler: ((rejectedSession: string) => Promise<string>) | null): void {
  onUnauthorized = handler;
}

export async function guestSend<T>(path: string, options: GuestSendOptions, renewed = false): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.sessionToken !== undefined) headers[GUEST_SESSION_HEADER] = options.sessionToken;
  if (options.idempotencyKey !== undefined) headers['Idempotency-Key'] = options.idempotencyKey;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  try {
    return await fetchJson<T>(backendUrl(GUEST_PREFIX + path), {
      method: options.method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (error) {
    const rejected = options.sessionToken;
    if (!(error instanceof ApiError && error.status === 401) || rejected === undefined || !onUnauthorized) throw error;
    const fresh = await onUnauthorized(rejected);
    if (renewed) throw error;
    return guestSend(path, { ...options, sessionToken: fresh }, true);
  }
}

export function scan(token: string): Promise<ScanResponse> {
  return guestSend('/scan', { method: 'POST', body: { token } });
}

export function readHome(sessionToken: string, signal?: AbortSignal): Promise<GuestHome> {
  return guestSend('/home', { method: 'GET', sessionToken, signal });
}

export function listRequests(sessionToken: string, signal?: AbortSignal): Promise<GuestTask[]> {
  return guestSend('/requests', { method: 'GET', sessionToken, signal });
}

export function createRequest(
  sessionToken: string,
  body: CreateRequestBody,
  idempotencyKey: string,
): Promise<GuestTask> {
  return guestSend('/requests', { method: 'POST', sessionToken, body, idempotencyKey });
}

export function cancelRequest(sessionToken: string, id: string, idempotencyKey: string): Promise<GuestTask> {
  return guestSend(`/requests/${encodeURIComponent(id)}/cancel`, { method: 'POST', sessionToken, idempotencyKey });
}

export function rateRequest(
  sessionToken: string,
  id: string,
  rating: number,
  idempotencyKey: string,
): Promise<GuestTask> {
  const body: RatingBody = { rating };
  return guestSend(`/requests/${encodeURIComponent(id)}/rating`, {
    method: 'POST',
    sessionToken,
    body,
    idempotencyKey,
  });
}
