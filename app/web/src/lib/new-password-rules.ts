/** 新密碼兩欄最多可以打幾個字，和登入頁的密碼欄相同。 */
export const NEW_PASSWORD_MAX_LENGTH = 32;

/** 新密碼至少幾碼，和後端的 `NewPasswordStrength.MIN_PASSWORD_LENGTH` 相同。 */
export const NEW_PASSWORD_MIN_LENGTH = 8;

/**
 * 改密碼畫面要逐條告訴同仁新密碼還差哪幾條，所以這裡的順序就是畫面由上到下排的順序。
 * 前三條的鍵對應後端 422 `new_password_too_weak` 回的規則代碼，之後要對照後端回絕的原因時查這裡：
 * `minLength` 是 `min_length`、`letter` 是 `letter_required`、`digit` 是 `digit_required`。
 * `match`（兩次輸入相同）只有畫面檢查，因為後端只收一個新密碼，沒有對應的代碼。
 */
export const NEW_PASSWORD_RULES = ['minLength', 'letter', 'digit', 'match'] as const;

export type NewPasswordRule = (typeof NEW_PASSWORD_RULES)[number];

/**
 * 前三條要和後端判斷新密碼強度的方式完全相同，畫面打勾了，後端就不會因為強度回絕：
 * 長度照瀏覽器字串的字元單位算（一個表情符號算兩碼）；英文字母與數字只認半形，
 * 全形字、中文、帶重音的字母都不算；只要密碼裡出現一個就算有，中間有換行也一樣。
 * 第四條要新密碼不是空的，因為兩欄都空著時雖然相同，卻不該讓這條打勾。
 */
export function checkNewPassword(newPassword: string, confirmPassword: string): Record<NewPasswordRule, boolean> {
  return {
    minLength: newPassword.length >= NEW_PASSWORD_MIN_LENGTH,
    letter: /[A-Za-z]/.test(newPassword),
    digit: /[0-9]/.test(newPassword),
    match: newPassword !== '' && newPassword === confirmPassword,
  };
}

/** 四條規則全部打勾，「確認」按鈕才能按。 */
export function allNewPasswordRulesPassed(rules: Record<NewPasswordRule, boolean>): boolean {
  return NEW_PASSWORD_RULES.every((rule) => rules[rule]);
}
