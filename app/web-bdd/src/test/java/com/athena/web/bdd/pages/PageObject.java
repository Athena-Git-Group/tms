package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;

/**
 * 各畫面 page object 的共同基底：一個 page object 負責一個畫面或面板「元素怎麼找、動作怎麼做」，
 * 步驟類別只說要做什麼，不自己寫定位方式。
 *
 * <p>找元素一律用使用者看得到的角色與文字（{@code getByRole}、{@code getByText}、{@code getByLabel}），
 * 不用版面座標，也不要求產品為了測試加 {@code data-testid}。畫面改版時只要改這裡，步驟不必動。
 *
 * <p>每次操作都向 {@link WebWorld} 取目前的分頁，不自己留著分頁：場景中途可能換分頁或換瀏覽器設定，
 * 留著的那一個會是已經關掉的。
 */
public abstract class PageObject {

    protected final WebWorld world;

    protected PageObject(WebWorld world) {
        this.world = world;
    }

    protected Page page() {
        return world.page();
    }
}
