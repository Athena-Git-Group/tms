import type { StaffRole } from '../api/types';
import { PATHS, type AppPath } from './paths';

/**
 * 進頁面前的檢查：依登入狀態決定「這一頁可以進」還是「要改去哪一頁」。
 * 寫成不碰瀏覽器與畫面的純計算，是為了能直接用單元測試驗每一種組合；
 * `router.tsx` 的每一條路徑都先問這裡，元件裡不另外寫判斷。
 *
 * 三條規則，由上往下判斷：
 * 1. 沒登入：只能在登入頁。
 * 2. 登入了但還沒設定新密碼：只能在改密碼畫面（任何角色都一樣）。
 * 3. 角色不是櫃檯、主管、值班主管：進不了外框（總覽看板、待派單頁），只能在登入頁。
 */

/** 總覽看板與待派單頁的規則完全相同，所以合成外框一區。 */
export type RouteArea = 'login' | 'changePassword' | 'shell';

export type AccessSession = { role: StaffRole; mustChangePassword: boolean };

export type RouteDecision = { allow: true } | { allow: false; redirectTo: AppPath };

export const DESK_ROLES: readonly StaffRole[] = ['frontdesk', 'manager', 'duty_manager'];

export function canUseDesk(role: StaffRole): boolean {
  return DESK_ROLES.includes(role);
}

const ALLOW: RouteDecision = { allow: true };

function onlyIn(allowed: RouteArea, allowedPath: AppPath, current: RouteArea): RouteDecision {
  return current === allowed ? ALLOW : { allow: false, redirectTo: allowedPath };
}

export function decideRoute(area: RouteArea, session: AccessSession | null): RouteDecision {
  if (session === null) return onlyIn('login', PATHS.login, area);
  if (session.mustChangePassword) return onlyIn('changePassword', PATHS.changePassword, area);
  if (!canUseDesk(session.role)) return onlyIn('login', PATHS.login, area);
  return onlyIn('shell', PATHS.board, area);
}
