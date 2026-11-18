import { apiGet, apiPost } from './client';
import type { ChangePasswordRequest, LoginRequest, LoginResponse, PublicHotel } from './types';

export function readPublicHotel(hotelCode: string): Promise<PublicHotel> {
  return apiGet<PublicHotel>(`/public/hotels/${encodeURIComponent(hotelCode)}`, { auth: false });
}

export function loginToFrontdesk(loginId: string, password: string, hotelCode: string): Promise<LoginResponse> {
  const body: LoginRequest = { loginId, password, client: 'frontdesk_web', hotelCode };
  return apiPost<LoginResponse>('/staff-accounts/login', body, { auth: false });
}

export function changeOwnPassword(newPassword: string): Promise<LoginResponse> {
  const body: ChangePasswordRequest = { newPassword };
  return apiPost<LoginResponse>('/staff-accounts/change-password', body);
}
