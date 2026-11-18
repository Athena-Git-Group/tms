import type { StaffRole } from '../api/types';

export type UnopenedPosition = 'sidebarNav' | 'sidebarSettings' | 'toolbar';

export interface UnopenedEntry {
  labelKey: 'shell.noticeBoard' | 'shell.lostAndFound' | 'shell.settings' | 'shell.createRequest';
  icon: 'bell' | 'bag' | 'chevron' | 'plus';
  position: UnopenedPosition;
  roles: 'all' | readonly StaffRole[];
}

export const UNOPENED_ENTRIES: readonly UnopenedEntry[] = [
  { labelKey: 'shell.noticeBoard', icon: 'bell', position: 'sidebarNav', roles: 'all' },
  { labelKey: 'shell.lostAndFound', icon: 'bag', position: 'sidebarNav', roles: 'all' },
  { labelKey: 'shell.settings', icon: 'chevron', position: 'sidebarSettings', roles: ['manager', 'duty_manager'] },
  { labelKey: 'shell.createRequest', icon: 'plus', position: 'toolbar', roles: 'all' },
];

export function visibleUnopenedEntries(position: UnopenedPosition, role: StaffRole | undefined): UnopenedEntry[] {
  return UNOPENED_ENTRIES.filter(
    (entry) => entry.position === position && (entry.roles === 'all' || (role !== undefined && entry.roles.includes(role))),
  );
}
