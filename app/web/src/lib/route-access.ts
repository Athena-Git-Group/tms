import type { StaffRole } from '../api/types';
import { PATHS, type AppPath } from './paths';

/**
 * 進頁面前的檢查：依登入狀態決定「這一頁可以進」還是「要改去哪一頁」。
 * 寫成不碰瀏覽器與畫面的純計算，是為了能直接用單元測試驗每一種組合；
 * `router.tsx` 的每一條路徑都先問這裡，元件裡不另外寫判斷。
 *
 * 先看登入的是不是網址上這一家飯店（`sessionAtHotel`）：飯店之間是隔離的，登入別家的分頁在這一家算沒登入。
 * 再照三條規則，由上往下判斷：
 * 1. 沒登入：只能在登入頁。
 * 2. 登入了但還沒設定新密碼：只能在改密碼畫面（任何角色都一樣）。
 * 3. 角色不是櫃檯、主管、值班主管：進不了外框（總覽看板、待派單頁），只能在登入頁。
 */

/** 總覽看板與待派單頁的規則完全相同，所以合成外框一區。 */
export type RouteArea = 'login' | 'changePassword' | 'shell';

export type AccessSession = { role: StaffRole; mustChangePassword: boolean };

/** `redirectTo` 是飯店識別代碼之後的那一段；呼叫端用 `hotelPath` 接回目前網址上的代碼，換頁後才會留在同一家飯店底下。 */
export type RouteDecision = { allow: true } | { allow: false; redirectTo: AppPath };

export const DESK_ROLES: readonly StaffRole[] = ['frontdesk', 'manager', 'duty_manager'];

export function canUseDesk(role: StaffRole): boolean {
  return DESK_ROLES.includes(role);
}

const ALLOW: RouteDecision = { allow: true };

function onlyIn(allowed: RouteArea, allowedPath: AppPath, current: RouteArea): RouteDecision {
  return current === allowed ? ALLOW : { allow: false, redirectTo: allowedPath };
}

/**
 * 這個分頁在網址上這一家飯店的登入狀態：登入的就是這一家才算數，登入的是別家時當作沒有登入。
 * 別家的登入狀態留著不動，回到那家的網址仍是登入的；在這一家登入成功時才換掉（見登入頁）。
 */
export function sessionAtHotel<S extends { hotelCode: string }>(session: S | null, hotelCode: string): S | null {
  return session !== null && session.hotelCode === hotelCode ? session : null;
}

export function decideRoute(area: RouteArea, session: AccessSession | null): RouteDecision {
  if (session === null) return onlyIn('login', PATHS.login, area);
  if (session.mustChangePassword) return onlyIn('changePassword', PATHS.changePassword, area);
  if (!canUseDesk(session.role)) return onlyIn('login', PATHS.login, area);
  return onlyIn('shell', PATHS.board, area);
}
