import { GUEST_PATHS, guestPath } from '../paths';

export type CameFrom = 'home' | 'list' | 'sent';

export interface RequestLink {
  to: string;
  state: { from: CameFrom };
}

const BACK_TO: Record<CameFrom, string> = {
  home: GUEST_PATHS.home,
  list: GUEST_PATHS.requests,
  sent: GUEST_PATHS.requests,
};

export function requestLink(qrToken: string, id: string, from: CameFrom): RequestLink {
  return { to: guestPath(qrToken, `requests/${encodeURIComponent(id)}`), state: { from } };
}

export function cameFrom(state: unknown): CameFrom | null {
  const from = typeof state === 'object' && state !== null ? (state as { from?: unknown }).from : undefined;
  return from === 'home' || from === 'list' || from === 'sent' ? from : null;
}

export function backTargetOf(from: CameFrom | null, qrToken: string): string {
  return guestPath(qrToken, BACK_TO[from ?? 'list']);
}
