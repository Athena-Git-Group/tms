/**
 * 櫃檯 Web 的四條路徑。畫面與換頁一律用這裡的常數與 `hotelPath`，不自己寫字串。
 *
 * 網址最前面是飯店識別代碼，例如 `/athena/login`、`/athena/board`；這裡的常數是代碼之後的那一段。
 * 網址沒有帶代碼時（例如直接打 `/login`）不提供這四頁，改顯示「輸入飯店代碼」頁（見 `router.tsx`）。
 */
export const PATHS = {
  /** 登入頁 */
  login: '/login',
  /** 改密碼畫面 */
  changePassword: '/change-password',
  /** 總覽看板 */
  board: '/board',
  /** 待派單頁 */
  triage: '/triage',
} as const;

export type AppPath = (typeof PATHS)[keyof typeof PATHS];

/** 路由設定裡代表飯店識別代碼的那一段的名稱。 */
export const HOTEL_CODE_PARAM = 'hotelCode';

/** 把某一頁接在飯店識別代碼底下；沒有代碼時就是那一頁本身的路徑。 */
export function hotelPath(hotelCode: string | undefined, path: AppPath): string {
  if (hotelCode === undefined || hotelCode === '') return path;
  return `/${encodeURIComponent(hotelCode)}${path}`;
}
