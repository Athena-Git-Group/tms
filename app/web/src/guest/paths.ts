export const GUEST_PATHS = {
  home: '',
  supply: 'supply',
  repair: 'repair',
  sent: 'sent',
  requests: 'requests',
  request: 'requests/:id',
  guide: 'guide/:key',
} as const;

export function guestPath(qrToken: string, sub = ''): string {
  const root = `/g/${encodeURIComponent(qrToken)}`;
  return sub === '' ? root : `${root}/${sub}`;
}
