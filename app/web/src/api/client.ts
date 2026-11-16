import { API_BASE_URL } from '../config';
import { useSessionStore } from '../stores/session';
import type { ApiErrorBody } from './types';

/**
 * 畫面不直接呼叫 `fetch`。
 * 各端點的函式放在 `api/` 底下，例如 `api/desk.ts`，而且都經過這個檔，
 * 登入憑證、防重送的鍵與錯誤的形狀才會每一支都一樣。
 * 收到錯誤之後畫面要怎麼反應由用到的功能決定，不寫在這裡。憑證失效要不要回登入頁就是一例。
 */

/** 事件串流 `/staff/stream` 不在這個前綴底下，要用 `backendUrl` 自己組網址。 */
const API_PREFIX = '/api/v1';

/** 設定值結尾帶了斜線時要先拿掉，否則接上路徑會變成 `//api/v1`，後端認不得。 */
const BACKEND_ORIGIN = API_BASE_URL.replace(/\/+$/, '');

/** 後端有回應但不是成功。畫面依 `code` 選提示，`message` 與 `context` 來自契約的 `Error`。 */
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

/**
 * 請求沒有送到後端，或回應沒有收完。斷網與後端沒開都是這一種。
 * 和 `ApiError` 分開，是因為這時畫面不知道後端有沒有處理，寫入要用同一把鍵重送才安全。
 */
export class NetworkError extends Error {
  constructor(cause: unknown) {
    super('連不上後端', { cause });
    this.name = 'NetworkError';
  }
}

/**
 * 回應不是契約的形狀時用這個 `code`。
 * 常見的情形是後端沒開，前面的代理回了 502 純文字，或把網頁本身當成 200 回來。
 */
export const UNKNOWN_ERROR_CODE = 'unknown_error';

const UNKNOWN_ERROR_BODY: ApiErrorBody = { code: UNKNOWN_ERROR_CODE, message: '' };

export interface RequestOptions {
  /** 登入、登入前查飯店名稱這類不需要身分的端點傳 `false`；不傳就帶登入憑證。 */
  auth?: boolean;
  signal?: AbortSignal;
}

export interface WriteOptions extends RequestOptions {
  /**
   * 逾時後要重送「同一次操作」時，呼叫端先用 `newIdempotencyKey()` 取一把，兩次都傳同一把，
   * 後端才會回放第一次的結果而不是再做一次。不傳就每次產生新的一把。
   */
  idempotencyKey?: string;
}

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

/** 事件串流不在 `/api/v1` 底下、也不走下面的 `send`，所以把組網址單獨開放出來。 */
export function backendUrl(path: string): string {
  return `${BACKEND_ORIGIN}${path}`;
}

/** 事件串流自己開 `fetch` 時也要帶同一份憑證；沒有登入時回空物件。 */
export function authHeaders(): Record<string, string> {
  const token = useSessionStore.getState().session?.token;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** @param path 契約上的路徑，不含 `/api/v1`，例如 `/desk/board`。 */
export function apiGet<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return send<T>('GET', path, undefined, {}, options);
}

/** 給後端不要求防重送的送出用，例如登入、設定新密碼。 */
export function apiPost<T>(path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
  return send<T>('POST', path, body, {}, options);
}

/** 派單、復原、當場回答沒帶 `Idempotency-Key` 會被後端拒絕，所以這三種寫入一律走這一支。 */
export function apiWrite<T>(path: string, body?: unknown, options: WriteOptions = {}): Promise<T> {
  const key = options.idempotencyKey ?? newIdempotencyKey();
  return send<T>('POST', path, body, { 'Idempotency-Key': key }, options);
}

async function send<T>(
  method: string,
  path: string,
  body: unknown,
  extraHeaders: Record<string, string>,
  options: RequestOptions,
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...extraHeaders };
  if (options.auth !== false) Object.assign(headers, authHeaders());
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  let text: string;
  try {
    response = await fetch(backendUrl(API_PREFIX + path), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: options.signal,
    });
    text = await response.text();
  } catch (cause) {
    // 換頁或關面板時呼叫端會自己取消請求，那不是連線問題，不能讓畫面跳出連不上的提示。
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    throw new NetworkError(cause);
  }

  if (!response.ok) throw new ApiError(response.status, toErrorBody(text));
  // 後端回 204 時內容是空的，空字串拿去解析會丟錯，所以先放行。
  if (text === '') return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    // 狀態是成功、內容卻不是 JSON，代表回話的不是後端。當成錯誤，畫面才不會拿一段網頁當資料用。
    throw new ApiError(response.status, UNKNOWN_ERROR_BODY);
  }
}

function toErrorBody(text: string): ApiErrorBody {
  try {
    const parsed: unknown = JSON.parse(text);
    if (isErrorBody(parsed)) return parsed;
  } catch {
    // 代理或閘道回的錯誤頁不是 JSON，和形狀不對的回應一樣當成認不得的錯誤。
  }
  return UNKNOWN_ERROR_BODY;
}

function isErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== 'object' || value === null) return false;
  const body = value as Record<string, unknown>;
  return typeof body.code === 'string' && typeof body.message === 'string';
}
