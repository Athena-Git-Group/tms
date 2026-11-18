/** 員工編號最多幾個字。 */
export const LOGIN_ID_MAX_LENGTH = 12;

/** 密碼最多幾個字。 */
export const PASSWORD_MAX_LENGTH = 32;

/**
 * 把員工編號欄打進來的字整理成員工編號：只截到上限字數。
 * 員工編號有分大小寫，跟員工 App 一樣照打的樣子用，不轉大小寫、也不去前後空白。
 */
export function normalizeLoginId(input: string): string {
  return input.slice(0, LOGIN_ID_MAX_LENGTH);
}

/**
 * 「登入」能不能按：兩欄都不能是空的，而且不是正在送出。跟員工 App 一樣，兩欄都不去空白。
 */
export function canSubmitLogin(form: { loginId: string; password: string; submitting: boolean }): boolean {
  return form.loginId !== '' && form.password !== '' && !form.submitting;
}
