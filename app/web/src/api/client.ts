import { useSessionStore } from '../stores/session';
import { backendUrl, fetchJson, newIdempotencyKey } from './errors';

export { ApiError, NetworkError, UNKNOWN_ERROR_CODE, backendUrl, newIdempotencyKey } from './errors';

const API_PREFIX = '/api/v1';

export interface RequestOptions {
  auth?: boolean;
  signal?: AbortSignal;
}

export interface WriteOptions extends RequestOptions {
  idempotencyKey?: string;
}

export function authHeaders(): Record<string, string> {
  const token = useSessionStore.getState().session?.token;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export function apiGet<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return send<T>('GET', path, undefined, {}, options);
}

export function apiPost<T>(path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
  return send<T>('POST', path, body, {}, options);
}

export function apiWrite<T>(path: string, body?: unknown, options: WriteOptions = {}): Promise<T> {
  const key = options.idempotencyKey ?? newIdempotencyKey();
  return send<T>('POST', path, body, { 'Idempotency-Key': key }, options);
}

function send<T>(
  method: string,
  path: string,
  body: unknown,
  extraHeaders: Record<string, string>,
  options: RequestOptions,
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...extraHeaders };
  if (options.auth !== false) Object.assign(headers, authHeaders());
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  return fetchJson<T>(backendUrl(API_PREFIX + path), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: options.signal,
  });
}
