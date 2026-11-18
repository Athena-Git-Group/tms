export const HOTEL_CODE_MIN_LENGTH = 3;
export const HOTEL_CODE_MAX_LENGTH = 32;

const HOTEL_CODE_FORMAT = /^[a-z][a-z0-9]*(-[a-z0-9]+)*$/;

const RESERVED_HOTEL_CODES: ReadonlySet<string> = new Set([
  'api', 'public', 'login', 'logout', 'password', 'www', 'admin', 'app', 'desk', 'staff',
  'guest', 'board', 'triage', 'static', 'assets', 'auth', 'health', 'actuator', 'status', 'docs',
  'help', 'support', 'mail', 'cdn', 'dev', 'test', 'staging', 'tms', 'index', 'change-password',
]);

export function normalizeHotelCode(input: string): string {
  return input.trim().toLowerCase();
}

export function isWellFormedHotelCode(code: string): boolean {
  return (
    code.length >= HOTEL_CODE_MIN_LENGTH &&
    code.length <= HOTEL_CODE_MAX_LENGTH &&
    HOTEL_CODE_FORMAT.test(code) &&
    !RESERVED_HOTEL_CODES.has(code)
  );
}
