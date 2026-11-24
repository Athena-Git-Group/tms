import { API_BASE_URL } from '../config';
import type { ApiErrorBody } from './types';

const BACKEND_ORIGIN = API_BASE_URL.replace(/\/+$/, '');

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly context: Record<string, unknown>;
  readonly details: NonNullable<ApiErrorBody['details']>;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code;
    this.context = body.context ?? {};
    this.details = body.details ?? [];
  }
}

export class NetworkError extends Error {
  constructor(cause: unknown) {
    super('連不上後端', { cause });
    this.name = 'NetworkError';
  }
}

export const UNKNOWN_ERROR_CODE = 'unknown_error';

const UNKNOWN_ERROR_BODY: ApiErrorBody = { code: UNKNOWN_ERROR_CODE, message: '' };

/**
 * 瀏覽器只在 https 與 localhost 提供 `crypto.randomUUID`。
 * 用 http 加區網位址連進來的 iPad 沒有它，所以要有一條自己用亂數組出同樣格式的退路。
 */
export function newIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();

  const bytes = crypto.getRandomValues(new Uint8Array(16));
  // 第 4 版 UUID 規定這兩個位置要放固定的版本與變體標記，其餘才是亂數。
  bytes[6] = (bytes[6]! & 0x0f) | 0x40;
  bytes[8] = (bytes[8]! & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return [
    hex.slice(0, 8),
    hex.slice(8, 12),
    hex.slice(12, 16),
    hex.slice(16, 20),
    hex.slice(20),
  ].join('-');
}

export function backendUrl(path: string): string {
  return `${BACKEND_ORIGIN}${path}`;
}

export async function fetchJson<T>(url: string, init: RequestInit): Promise<T> {
  return (await fetchJsonResponse<T>(url, init)).body;
}

export async function fetchJsonResponse<T>(url: string, init: RequestInit): Promise<{ body: T; headers: Headers }> {
  let response: Response;
  let text: string;
  try {
    response = await fetch(url, init);
    text = await response.text();
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    throw new NetworkError(cause);
  }

  if (!response.ok) throw new ApiError(response.status, toErrorBody(text));
  if (text === '') return { body: undefined as T, headers: response.headers };
  try {
    return { body: JSON.parse(text) as T, headers: response.headers };
  } catch {
    throw new ApiError(response.status, UNKNOWN_ERROR_BODY);
  }
}

function toErrorBody(text: string): ApiErrorBody {
  try {
    const parsed: unknown = JSON.parse(text);
    if (isErrorBody(parsed)) return parsed;
  } catch {
  }
  return UNKNOWN_ERROR_BODY;
}

function isErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== 'object' || value === null) return false;
  const body = value as Record<string, unknown>;
  return typeof body.code === 'string' && typeof body.message === 'string';
}
