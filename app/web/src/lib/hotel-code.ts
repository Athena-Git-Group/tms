/**
 * 「輸入飯店代碼」頁用的代碼整理與檢查。
 *
 * 規則照後端 `common/HotelCode.java`：長度 3 到 32，只用小寫英文字母、數字與連字號，第一個字是英文字母，
 * 不以連字號結尾、不連用兩個連字號，也不能是保留字。後端比對時一字不差，不替人修剪或轉小寫；
 * 這裡是人手打的輸入框，所以先去掉前後空白、轉成小寫再檢查，送進網址的就是後端認得的寫法。
 * 保留字是網址上已有用途的字（例如 `login`、`board`、`api`），放進網址第一段會被當成頁面或轉給後端，
 * 所以在這裡就擋下；清單和後端 `HotelCode.RESERVED` 一致，要改得兩邊一起改。
 */
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
