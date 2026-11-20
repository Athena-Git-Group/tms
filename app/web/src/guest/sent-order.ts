import { readJson, writeJson } from '../lib/browser-storage';

/**
 * 這支手機送出需求的先後（task id），跟著分頁、鍵帶 QR 代碼。後端時鐘固定或同一秒送出的幾張送出時間相同，
 * 後端分不出先後，清單與首頁進度卡要靠這份紀錄排。儲存空間被封鎖時只記在記憶體。
 */
const KEY_PREFIX = 'tms.guest.sent.';

const inMemory = new Map<string, string[]>();

export function readSentOrder(qrToken: string): string[] {
  const stored = readJson('session', KEY_PREFIX + qrToken);
  if (Array.isArray(stored) && stored.every((id) => typeof id === 'string')) return stored;
  return inMemory.get(qrToken) ?? [];
}

export function recordSent(qrToken: string, ids: readonly string[]): void {
  const order = [...readSentOrder(qrToken).filter((id) => !ids.includes(id)), ...ids];
  inMemory.set(qrToken, order);
  writeJson('session', KEY_PREFIX + qrToken, order);
}
