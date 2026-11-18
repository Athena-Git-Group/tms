package com.athena.tms.common;

import static com.athena.tms.common.NewPasswordStrength.DIGIT_REQUIRED;
import static com.athena.tms.common.NewPasswordStrength.LETTER_REQUIRED;
import static com.athena.tms.common.NewPasswordStrength.MIN_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 確認新密碼強度規則對各種密碼回報的沒過規則與順序。
 *
 * <p>規則代碼是畫面對照提示用的字串，所以這裡也直接比對字串本身，代碼一改測試就會失敗。
 */
class NewPasswordStrengthTest {

    @Test
    void 規則代碼和畫面對照的字串一致() {
        assertThat(MIN_LENGTH).isEqualTo("min_length");
        assertThat(LETTER_REQUIRED).isEqualTo("letter_required");
        assertThat(DIGIT_REQUIRED).isEqualTo("digit_required");
        assertThat(NewPasswordStrength.MIN_PASSWORD_LENGTH).isEqualTo(8);
    }

    @Test
    void 三條都過時回空清單() {
        assertThat(NewPasswordStrength.failedRules("Athena2026")).isEmpty();
    }

    @Test
    void 剛好8碼算過長度() {
        assertThat(NewPasswordStrength.failedRules("abcdef12")).isEmpty();
    }

    @Test
    void 只有長度不足() {
        assertThat(NewPasswordStrength.failedRules("abcde12")).containsExactly(MIN_LENGTH);
    }

    @Test
    void 只缺英文字母() {
        assertThat(NewPasswordStrength.failedRules("12345678")).containsExactly(LETTER_REQUIRED);
    }

    @Test
    void 只缺數字() {
        assertThat(NewPasswordStrength.failedRules("athenaaa")).containsExactly(DIGIT_REQUIRED);
    }

    @Test
    void 長度不足又缺數字時依規則順序列出() {
        assertThat(NewPasswordStrength.failedRules("ab")).containsExactly(MIN_LENGTH, DIGIT_REQUIRED);
    }

    @Test
    void 空字串三條都沒過且依規則順序列出() {
        assertThat(NewPasswordStrength.failedRules(""))
                .containsExactly(MIN_LENGTH, LETTER_REQUIRED, DIGIT_REQUIRED);
    }

    @Test
    void 含換行字元的合格密碼照樣算過() {
        assertThat(NewPasswordStrength.failedRules("\nAthena2026")).isEmpty();
        assertThat(NewPasswordStrength.failedRules("Athena\n2026")).isEmpty();
        assertThat(NewPasswordStrength.failedRules("12345678\n")).containsExactly(LETTER_REQUIRED);
    }

    @Test
    void 非ASCII的字母與數字不算() {
        // 帶重音的字母、中文、全形英文字母都不算英文字母；全形數字不算數字
        assertThat(NewPasswordStrength.failedRules("éàüçñ123")).containsExactly(LETTER_REQUIRED);
        assertThat(NewPasswordStrength.failedRules("飯店密碼一二三四56")).containsExactly(LETTER_REQUIRED);
        assertThat(NewPasswordStrength.failedRules("ＡＢＣＤ1234")).containsExactly(LETTER_REQUIRED);
        assertThat(NewPasswordStrength.failedRules("abcd１２３４")).containsExactly(DIGIT_REQUIRED);
    }
}
