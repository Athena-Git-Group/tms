import type { StaffRole } from '../api/types';

export type UnopenedPosition = 'sidebarNav' | 'toolbar';

export interface UnopenedEntry {
  labelKey: 'shell.noticeBoard' | 'shell.lostAndFound' | 'shell.createRequest';
  icon: 'bell' | 'bag' | 'plus';
  position: UnopenedPosition;
  roles: 'all' | readonly StaffRole[];
}

export const UNOPENED_ENTRIES: readonly UnopenedEntry[] = [
  { labelKey: 'shell.noticeBoard', icon: 'bell', position: 'sidebarNav', roles: 'all' },
  { labelKey: 'shell.lostAndFound', icon: 'bag', position: 'sidebarNav', roles: 'all' },
  { labelKey: 'shell.createRequest', icon: 'plus', position: 'toolbar', roles: 'all' },
];

export function visibleUnopenedEntries(position: UnopenedPosition, role: StaffRole | undefined): UnopenedEntry[] {
  return UNOPENED_ENTRIES.filter(
    (entry) => entry.position === position && (entry.roles === 'all' || (role !== undefined && entry.roles.includes(role))),
  );
}
