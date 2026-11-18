import { apiGet, apiPost } from './client';
import type { ChangePasswordRequest, LoginRequest, LoginResponse, PublicHotel } from './types';

/** 登入前查飯店名稱。代碼對不到飯店時後端回 404，由呼叫端當成「沒有這一行」。 */
export function readPublicHotel(hotelCode: string): Promise<PublicHotel> {
  return apiGet<PublicHotel>(`/public/hotels/${encodeURIComponent(hotelCode)}`, { auth: false });
}

/**
 * 櫃檯 Web 的登入一律帶 `client`=`frontdesk_web` 與網址上的飯店識別代碼。
 * 帶了 `client`，後端才會把不能用櫃檯畫面的角色當成帳密錯誤擋下；不帶的話這些角色也登得進來。
 * 帶了飯店代碼，後端只在那一家找這個員工編號，別家相同編號的帳號不受影響；代碼對不到飯店時和帳密錯誤一樣回 401。
 */
export function loginToFrontdesk(loginId: string, password: string, hotelCode: string): Promise<LoginResponse> {
  const body: LoginRequest = { loginId, password, client: 'frontdesk_web', hotelCode };
  return apiPost<LoginResponse>('/staff-accounts/login', body, { auth: false });
}

/**
 * 拿臨時密碼登入的人設定自己的新密碼。這時身分由登入換得的憑證證明，後端不要求目前密碼，所以只送新密碼，
 * 臨時密碼也就不必留在畫面或瀏覽器裡。成功時回應和登入相同，帶一組新的憑證，舊的那組立即失效。
 */
export function changeOwnPassword(newPassword: string): Promise<LoginResponse> {
  const body: ChangePasswordRequest = { newPassword };
  return apiPost<LoginResponse>('/staff-accounts/change-password', body);
}
