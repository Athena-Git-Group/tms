package com.athena.web.bdd.steps.guest.shell;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.假設;

public class ShellGivenSteps {

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final ShellWhenSteps when;

    public ShellGivenSteps(WebWorld world, GuestScenario scenario, ShellWhenSteps when) {
        this.world = world;
        this.scenario = scenario;
        this.phone = new GuestPhone(world, scenario);
        this.when = when;
    }

    @假設("這支手機的瀏覽器語言是 {string}，還沒有選過介面語言")
    public void browserLanguage(String locale) {
        world.openContext(options -> options.setLocale(locale.strip()));
    }

    @假設("{string} 房的住客已打開首頁，語言選單開著")
    public void homeWithLanguageMenuOpen(String roomNo) {
        phone.openHome(roomNo);
        when.tapLanguageCapsule();
    }

    @假設("{string} 房的另一支手機也打開了首頁，它的介面語言是 {string}")
    public void secondPhoneWithLanguage(String roomNo, String language) {
        Page first = world.page();
        Page second = world.openContext();
        world.session().putLocalItem(second, GuestFrame.LANGUAGE_KEY, GuestTerms.languageCode(language));
        phone.openHome(roomNo);
        scenario.rememberSecondPhone(second);
        world.switchTo(first);
        scenario.rememberPhoneRoom(roomNo);
    }
}
