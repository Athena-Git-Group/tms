import { ApiError, backendUrl, fetchJsonResponse } from '../../api/errors';
import { quotaFromHeaders } from '../lib/quota';
import type { CreateRequestBody, GuestHome, GuestRequestList, GuestTask, RatingBody, ScanResponse } from './types';

const GUEST_PREFIX = '/guest';

export const GUEST_SESSION_HEADER = 'X-Guest-Session';

export interface GuestSendOptions {
  method: 'GET' | 'POST';
  body?: unknown;
  sessionToken?: string;
  idempotencyKey?: string;
  signal?: AbortSignal;
}

export interface SessionRenewal {
  renew: (rejectedSession: string) => Promise<string>;
  renewedSessionRejected: (error: ApiError) => void;
}

let renewal: SessionRenewal | null = null;

export function setSessionRenewal(next: SessionRenewal | null): void {
  renewal = next;
}

type GuestCredential = { kind: 'held'; sessionToken: string } | { kind: 'renewed'; sessionToken: string };

async function exchange<T>(path: string, options: GuestSendOptions): Promise<{ body: T; headers: Headers }> {
  const credential: GuestCredential | null =
    options.sessionToken === undefined ? null : { kind: 'held', sessionToken: options.sessionToken };
  return attempt<T>(path, options, credential);
}

async function attempt<T>(
  path: string,
  options: GuestSendOptions,
  credential: GuestCredential | null,
): Promise<{ body: T; headers: Headers }> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (credential !== null) headers[GUEST_SESSION_HEADER] = credential.sessionToken;
  if (options.idempotencyKey !== undefined) headers['Idempotency-Key'] = options.idempotencyKey;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  try {
    return await fetchJsonResponse<T>(backendUrl(GUEST_PREFIX + path), {
      method: options.method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 401) || credential === null || !renewal) throw error;
    if (credential.kind === 'renewed') {
      renewal.renewedSessionRejected(error);
      throw error;
    }
    const fresh = await renewal.renew(credential.sessionToken);
    return attempt<T>(path, options, { kind: 'renewed', sessionToken: fresh });
  }
}

async function guestSend<T>(path: string, options: GuestSendOptions): Promise<T> {
  return (await exchange<T>(path, options)).body;
}

export function scan(token: string): Promise<ScanResponse> {
  return guestSend('/scan', { method: 'POST', body: { token } });
}

export function readHome(sessionToken: string, signal?: AbortSignal): Promise<GuestHome> {
  return guestSend('/home', { method: 'GET', sessionToken, signal });
}

export async function listRequests(sessionToken: string, signal?: AbortSignal): Promise<GuestRequestList> {
  const { body, headers } = await exchange<GuestTask[]>('/requests', { method: 'GET', sessionToken, signal });
  return { requests: body, quota: quotaFromHeaders(headers) };
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
