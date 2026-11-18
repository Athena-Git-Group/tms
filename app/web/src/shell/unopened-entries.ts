import type { StaffRole } from '../api/types';

/** 尚未開放的入口畫在外框的哪個位置。 */
export type UnopenedPosition = 'sidebarNav' | 'sidebarSettings' | 'toolbar';

export interface UnopenedEntry {
  /** 畫面文字的 i18n 鍵。 */
  labelKey: 'shell.noticeBoard' | 'shell.lostAndFound' | 'shell.settings' | 'shell.createRequest';
  /** 圖示名稱，對應外框的線條圖示。 */
  icon: 'bell' | 'bag' | 'chevron' | 'plus';
  position: UnopenedPosition;
  /** 看得到這個入口的角色；`'all'` 連尚未取得登入資料時也畫。 */
  roles: 'all' | readonly StaffRole[];
}

/**
 * 這一版先照雛形把入口畫出來，但點了沒有任何反應，也不跳「尚未開放」之類的提示：
 * 使用者裁決是讓畫面和雛形一致，不替還沒做的功能多設計一種說法。
 * 之後哪個功能開放，就把它從這份清單拿掉，改在外框裡接上真正的按鈕或連結，
 * 不要留在清單裡再加點擊處理，否則「尚未開放」的清單會混進已經能用的入口。
 * 清單內的順序就是畫在同一個位置時由上到下（由左到右）的順序。
 */
export const UNOPENED_ENTRIES: readonly UnopenedEntry[] = [
  { labelKey: 'shell.noticeBoard', icon: 'bell', position: 'sidebarNav', roles: 'all' },
  { labelKey: 'shell.lostAndFound', icon: 'bag', position: 'sidebarNav', roles: 'all' },
  { labelKey: 'shell.settings', icon: 'chevron', position: 'sidebarSettings', roles: ['manager', 'duty_manager'] },
  { labelKey: 'shell.createRequest', icon: 'plus', position: 'toolbar', roles: 'all' },
];

/** 某個位置上、這個角色看得到的尚未開放入口；沒有登入資料時只有 `roles: 'all'` 的入口。 */
export function visibleUnopenedEntries(position: UnopenedPosition, role: StaffRole | undefined): UnopenedEntry[] {
  return UNOPENED_ENTRIES.filter(
    (entry) => entry.position === position && (entry.roles === 'all' || (role !== undefined && entry.roles.includes(role))),
  );
}
