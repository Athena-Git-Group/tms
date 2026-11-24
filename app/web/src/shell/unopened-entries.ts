import type { StaffRole } from '../api/types';

export type UnopenedPosition = 'sidebarNav';

export interface UnopenedEntry {
  labelKey: 'shell.noticeBoard' | 'shell.lostAndFound';
  icon: 'bell' | 'bag';
  position: UnopenedPosition;
  roles: 'all' | readonly StaffRole[];
}

export const UNOPENED_ENTRIES: readonly UnopenedEntry[] = [
  { labelKey: 'shell.noticeBoard', icon: 'bell', position: 'sidebarNav', roles: 'all' },
  { labelKey: 'shell.lostAndFound', icon: 'bag', position: 'sidebarNav', roles: 'all' },
];

export function visibleUnopenedEntries(position: UnopenedPosition, role: StaffRole | undefined): UnopenedEntry[] {
  return UNOPENED_ENTRIES.filter(
    (entry) => entry.position === position && (entry.roles === 'all' || (role !== undefined && entry.roles.includes(role))),
  );
}
