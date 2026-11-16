/** 櫃檯 Web 的四條路徑。畫面與換頁一律用這裡的常數，不自己寫字串。 */
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
