import { GUEST_PATHS, guestPath } from '../paths';

/** 住客從哪裡進進度頁：首頁的進度卡、我的需求清單、一項的送出成功頁。 */
export type CameFrom = 'home' | 'list' | 'sent';

/** 進度頁連結：網址加上記在 location state 的來處（存在 `history.state`，重新整理後還在）。 */
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

/** 讀 location state 裡的來處；不是這三種（例如從歷史記錄另開分頁、沒有 state）時是 `null`。 */
export function cameFrom(state: unknown): CameFrom | null {
  const from = typeof state === 'object' && state !== null ? (state as { from?: unknown }).from : undefined;
  return from === 'home' || from === 'list' || from === 'sent' ? from : null;
}

/** 進度頁返回的去處：回到進來的地方；沒有紀錄時回清單。 */
export function backTargetOf(from: CameFrom | null, qrToken: string): string {
  return guestPath(qrToken, BACK_TO[from ?? 'list']);
}
