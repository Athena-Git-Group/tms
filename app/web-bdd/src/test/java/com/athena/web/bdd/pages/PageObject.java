package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;

public abstract class PageObject {

    protected final WebWorld world;

    protected PageObject(WebWorld world) {
        this.world = world;
    }

    protected Page page() {
        return world.page();
    }
}
