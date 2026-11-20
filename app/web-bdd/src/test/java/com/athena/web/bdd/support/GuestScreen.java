package com.athena.web.bdd.support;

import java.util.Arrays;
import java.util.Optional;

/**
 * 住客規格裡 {@code 頁} 參數的七種說法（{@code specs/truth/features/guest-web/dsl.md}「`頁` 參數的說法」），
 * 各自的網址那一段與子頁標題列上的頁標題。
 *
 * <p>無法使用頁與重試狀態沒有自己的網址，是開 {@code /g/{代碼}} 換不到 session 時留在原網址上的畫面；
 * 送出成功頁有網址但只有送出之後才進得去，所以句型裡「停在」「打開」的頁只會是 {@link #openable()} 的那幾頁。
 */
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

    /** 規格裡的說法，例如「備品補充頁」。 */
    public String term() {
        return term;
    }

    /** {@code /g/{代碼}} 之後那一段；沒有自己網址的畫面是空的。 */
    public Optional<String> sub() {
        return Optional.ofNullable(sub);
    }

    /** 子頁標題列上的頁標題（繁體中文）；首頁、送出成功頁與進入層沒有標題列，是空的。 */
    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    /** 「停在」「打開」這兩種句型可以直接開網址到的頁：首頁、備品補充頁、設備報修頁、我的需求清單。 */
    public boolean openable() {
        return this == HOME || this == SUPPLY || this == REPAIR || this == REQUESTS;
    }
}
