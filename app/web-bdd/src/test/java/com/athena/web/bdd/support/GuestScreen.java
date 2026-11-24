package com.athena.web.bdd.support;

import java.util.Arrays;
import java.util.Optional;

public enum GuestScreen {

    HOME("首頁", GuestApp.HOME, null),

    SUPPLY("備品補充頁", GuestApp.SUPPLY, "備品補充"),

    REPAIR("設備報修頁", GuestApp.REPAIR, "設備報修"),

    REQUESTS("我的需求清單", GuestApp.REQUESTS, "我的需求"),

    SENT("送出成功頁", GuestApp.SENT, null),

    UNAVAILABLE("無法使用頁", null, null),

    RETRY("重試狀態", null, null);

    private final String term;

    private final String sub;

    private final String title;

    GuestScreen(String term, String sub, String title) {
        this.term = term;
        this.sub = sub;
        this.title = title;
    }

    public static GuestScreen ofTerm(String term) {
        String wanted = term.strip();
        return Arrays.stream(values())
                .filter(screen -> screen.term.equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("住客的頁只有 "
                        + Arrays.stream(values()).map(GuestScreen::term).toList() + "，收到「" + term + "」"));
    }

    public String term() {
        return term;
    }

    public Optional<String> sub() {
        return Optional.ofNullable(sub);
    }

    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    public boolean openable() {
        return this == HOME || this == SUPPLY || this == REPAIR || this == REQUESTS;
    }
}
