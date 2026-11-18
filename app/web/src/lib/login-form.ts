export const LOGIN_ID_MAX_LENGTH = 12;

export const PASSWORD_MAX_LENGTH = 32;

export function normalizeLoginId(input: string): string {
  return input.slice(0, LOGIN_ID_MAX_LENGTH);
}

export function canSubmitLogin(form: { loginId: string; password: string; submitting: boolean }): boolean {
  return form.loginId !== '' && form.password !== '' && !form.submitting;
}
