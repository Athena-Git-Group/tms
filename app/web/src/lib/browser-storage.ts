/**
 * 在封鎖儲存的瀏覽器裡，光是取用 `sessionStorage` 或 `localStorage` 這個名字就會丟錯。
 * 所以每個函式收的是「要哪一種」，由這個檔在自己的 try 裡去取，丟錯才攔得住。
 */

export type StorageKind = 'session' | 'local';

function storageOf(kind: StorageKind): Storage {
  return kind === 'session' ? window.sessionStorage : window.localStorage;
}

export function readText(kind: StorageKind, key: string): string | null {
  try {
    return storageOf(kind).getItem(key);
  } catch {
    return null;
  }
}

export function writeText(kind: StorageKind, key: string, value: string): void {
  try {
    storageOf(kind).setItem(key, value);
  } catch {
  }
}

export function readJson(kind: StorageKind, key: string): unknown {
  const raw = readText(kind, key);
  if (raw === null) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function writeJson(kind: StorageKind, key: string, value: unknown): void {
  writeText(kind, key, JSON.stringify(value));
}

export function removeItem(kind: StorageKind, key: string): void {
  try {
    storageOf(kind).removeItem(key);
  } catch {
  }
}
