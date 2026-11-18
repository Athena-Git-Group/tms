package com.athena.web.bdd.steps.account;

import com.athena.web.bdd.pages.LoginPage.Submission;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 這個場景在登入頁按「登入」送出過的請求與回應，依送出的先後。
 *
 * <p>When 句把每一次送出記在這裡，Then 句拿來檢查送出的 {@code loginId} 是不是和輸入的照字面相同、帶的
 * {@code client}、回應的狀態碼與內容。這個類別由 Cucumber 在每個場景建一個新的，
 * 用建構子參數拿到它的步驟類別共用同一個。
 */
public class LoginAttempts {

    private final List<Submission> submissions = new ArrayList<>();

    public void add(Submission submission) {
        submissions.add(submission);
    }

    /** 最近一次送出；這個場景還沒送出過時是空的。 */
    public Optional<Submission> last() {
        return submissions.isEmpty() ? Optional.empty() : Optional.of(submissions.get(submissions.size() - 1));
    }

    /** 最近一次送出，沒有就直接失敗。 */
    public Submission requireLast() {
        return last().orElseThrow(() -> new IllegalStateException("這個場景還沒有在登入頁按過「登入」"));
    }

    public List<Submission> all() {
        return List.copyOf(submissions);
    }
}
