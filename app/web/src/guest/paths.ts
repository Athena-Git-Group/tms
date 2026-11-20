/**
 * 住客區每一頁都在 `/g/{代碼}/…` 底下、都帶著代碼：子頁重新整理或從歷史記錄打開時，
 * 頁面才能用網址上的代碼重新換 session，住客不用再掃一次。
 */
export const GUEST_PATHS = {
  home: '',
  supply: 'supply',
  repair: 'repair',
  sent: 'sent',
  requests: 'requests',
  request: 'requests/:id',
  guide: 'guide/:key',
} as const;

/** `sub` 是已代入參數的子路徑，例如 `requests/42`；不給就是首頁。 */
export function guestPath(qrToken: string, sub = ''): string {
  const root = `/g/${encodeURIComponent(qrToken)}`;
  return sub === '' ? root : `${root}/${sub}`;
}
