import type { NavigateFunction } from 'react-router';
import { ApiError, newIdempotencyKey } from '../../api/errors';
import { type FormNotice, limitNotice, noticeText, quotaNotice } from './quota';
import { GUEST_PATHS, guestPath } from '../paths';

export type SubmitResult =
  | { kind: 'all-sent'; created: string[] }
  | { kind: 'stopped-by-limit'; created: string[] }
  | { kind: 'stopped-by-network'; created: string[] };

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

export function resultNotice(result: SubmitResult): FormNotice | null {
  return NOTICE_OF_RESULT[result.kind](result.created.length);
}

export function goToSent(navigate: NavigateFunction, qrToken: string, ids: readonly string[]): void {
  void navigate(`${guestPath(qrToken, GUEST_PATHS.sent)}?ids=${ids.map(encodeURIComponent).join(',')}`);
}
