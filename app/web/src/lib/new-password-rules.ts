export const NEW_PASSWORD_MAX_LENGTH = 32;

export const NEW_PASSWORD_MIN_LENGTH = 8;

export const NEW_PASSWORD_RULES = ['minLength', 'letter', 'digit', 'match'] as const;

export type NewPasswordRule = (typeof NEW_PASSWORD_RULES)[number];

export function checkNewPassword(newPassword: string, confirmPassword: string): Record<NewPasswordRule, boolean> {
  return {
    minLength: newPassword.length >= NEW_PASSWORD_MIN_LENGTH,
    letter: /[A-Za-z]/.test(newPassword),
    digit: /[0-9]/.test(newPassword),
    match: newPassword !== '' && newPassword === confirmPassword,
  };
}

export function allNewPasswordRulesPassed(rules: Record<NewPasswordRule, boolean>): boolean {
  return NEW_PASSWORD_RULES.every((rule) => rules[rule]);
}
