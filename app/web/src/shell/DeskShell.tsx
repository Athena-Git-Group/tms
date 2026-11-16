import { Outlet } from 'react-router';

/**
 * 登入後每一頁共用的外框。總覽看板與待派單頁都畫在 `<Outlet />` 的位置，
 * 所以在兩頁之間切換時外框不會重建，掛在外框上的東西也不會被關掉重開。
 *
 * 側欄、工具列、提醒列、派單浮條、對話框，以及帳號面板、詳情面板、當場回答面板都掛在這裡
 * （元件本身放在 `shell/` 或對應的 `features/` 底下）。目前是空殼。
 */
export function DeskShell() {
  return (
    <div data-shell="desk" className="min-h-screen bg-bg">
      <Outlet />
    </div>
  );
}
