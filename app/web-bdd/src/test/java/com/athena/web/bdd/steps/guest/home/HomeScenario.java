package com.athena.web.bdd.steps.guest.home;

import java.util.List;

/** 「首頁」的句子之間要傳的東西：住客點下去之前的網址與畫面文字。一個場景一份。 */
public final class HomeScenario {

    /** 點之前的樣子。 */
    public record Before(String url, List<String> lines) {
    }

    private Before beforeTap;

    public void rememberBeforeTap(String url, List<String> lines) {
        beforeTap = new Before(url, List.copyOf(lines));
    }

    public Before beforeTap() {
        if (beforeTap == null) {
            throw new IllegalStateException("這個場景的住客沒有點過房號膠囊或即將開放的入口，沒有「點之前」的畫面可以比");
        }
        return beforeTap;
    }
}
