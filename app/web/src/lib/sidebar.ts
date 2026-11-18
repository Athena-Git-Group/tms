/**
 * 側欄現在該開還是收。
 *
 * 使用者還沒自己按過開關時（`sidebarOpenByUser` 是 null），待派單頁預設打開、其他頁預設收起；
 * 按過之後兩頁都照使用者的選擇。
 */
export function sidebarOpen(page: string, sidebarOpenByUser: boolean | null): boolean {
  return sidebarOpenByUser ?? page === 'triage';
}
