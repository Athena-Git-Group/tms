export const PATHS = {
  login: '/login',
  changePassword: '/change-password',
  board: '/board',
  triage: '/triage',
} as const;

export type AppPath = (typeof PATHS)[keyof typeof PATHS];

export const HOTEL_CODE_PARAM = 'hotelCode';

export function hotelPath(hotelCode: string | undefined, path: AppPath): string {
  if (hotelCode === undefined || hotelCode === '') return path;
  return `/${encodeURIComponent(hotelCode)}${path}`;
}
