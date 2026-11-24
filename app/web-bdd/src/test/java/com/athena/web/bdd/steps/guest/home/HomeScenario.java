package com.athena.web.bdd.steps.guest.home;

import java.util.List;

public final class HomeScenario {

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
