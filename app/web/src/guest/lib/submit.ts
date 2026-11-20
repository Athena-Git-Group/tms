import type { NavigateFunction } from 'react-router';
import { ApiError, newIdempotencyKey } from '../../api/errors';
import { type FormNotice, limitNotice, noticeText, quotaNotice } from './quota';
import { GUEST_PATHS, guestPath } from '../paths';

/**
 * 一批送出的結果。三種都帶已經建立的需求 id（依送出先後），停下來的兩種代表後面的沒有送。
 * 連不上、`5xx` 與其他意外的拒絕都算 `stopped-by-network`：畫面上說的都是「沒有送出，檢查網路再試」。
 */
export type SubmitResult =
  | { kind: 'all-sent'; created: string[] }
  | { kind: 'stopped-by-limit'; created: string[] }
  | { kind: 'stopped-by-network'; created: string[] };

/**
 * 一張一張送，前一張回來才送下一張，每張自己一把冪等鍵；有一張失敗就停，前面建立的照樣算數。
 * 後端一次只收一項，一起送的話其中一張被限流時分不出哪幾張已經建立。
 */
export async function submitSequentially<T>(
  items: readonly T[],
  send: (item: T, idempotencyKey: string) => Promise<{ id: string }>,
  newKey: () => string = newIdempotencyKey,
): Promise<SubmitResult> {
  const created: string[] = [];
  for (const item of items) {
    try {
      created.push((await send(item, newKey())).id);
    } catch (failure) {
      const limited = failure instanceof ApiError && failure.status === 429;
      return { kind: limited ? 'stopped-by-limit' : 'stopped-by-network', created };
    }
  }
  return { kind: 'all-sent', created };
}

const NOTICE_OF_RESULT: { [K in SubmitResult['kind']]: (sent: number) => FormNotice | null } = {
  'all-sent': () => null,
  'stopped-by-limit': (sent) => limitNotice(quotaNotice({ sent })),
  'stopped-by-network': (sent) => ({
    tone: 'error',
    title: noticeText(sent > 0 ? 'guest.notSent.partialTitle' : 'guest.notSent.title', { sent }),
    body: noticeText('guest.notSent.body'),
  }),
};

/** 停下來時表單下方要顯示的提示框；全部送出時是 `null`。 */
export function resultNotice(result: SubmitResult): FormNotice | null {
  return NOTICE_OF_RESULT[result.kind](result.created.length);
}

/** 全部建立成功之後換到送出成功頁，網址帶著建立的需求 id。 */
export function goToSent(navigate: NavigateFunction, qrToken: string, ids: readonly string[]): void {
  void navigate(`${guestPath(qrToken, GUEST_PATHS.sent)}?ids=${ids.map(encodeURIComponent).join(',')}`);
}
