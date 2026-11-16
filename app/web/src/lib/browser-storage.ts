/**
 * 讀寫瀏覽器儲存空間。儲存空間被停用、滿了或內容壞掉時不讓畫面掛掉：
 * 讀不到就當作沒有，寫不進就略過。
 *
 * 在封鎖儲存的瀏覽器裡，光是取用 `sessionStorage` 或 `localStorage` 這個名字就會丟錯。
 * 所以每個函式收的是「要哪一種」，由這個檔在自己的 try 裡去取，丟錯才攔得住。
 */

/** `session` 是每個分頁各一份、關掉分頁就清掉的那一種；`local` 是這台裝置長期保存的那一種。 */
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
    // 寫不進去時畫面照常運作，只是重新整理後不會保留。
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
    // 同上。
  }
}
