package com.athena.web.bdd.steps.account;

import com.athena.web.bdd.pages.LoginPage.Submission;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LoginAttempts {

    private final List<Submission> submissions = new ArrayList<>();

    public void add(Submission submission) {
        submissions.add(submission);
    }

    public Optional<Submission> last() {
        return submissions.isEmpty() ? Optional.empty() : Optional.of(submissions.get(submissions.size() - 1));
    }

    public Submission requireLast() {
        return last().orElseThrow(() -> new IllegalStateException("這個場景還沒有在登入頁按過「登入」"));
    }

    public List<Submission> all() {
        return List.copyOf(submissions);
    }
}
