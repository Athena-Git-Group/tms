package com.athena.app.bdd.steps.pool;

/**
 * 待認領模組的情境內共用狀態。目前只有一件事：畫面上目前選中的分類磚。
 *
 * <p>為什麼需要：列表斷言的「與後端回應一致」要拿「App 當下實際發出的那個查詢」
 * 去讀回（契約 listDeptPool 的 category 參數會改變 items），但「依序為…」這個
 * Then 句型不帶分類參數，只能由點分類磚的 When 把選擇留下來。Cucumber 以
 * constructor injection 讓同情境的 step 類別共用同一個實例。
 */
public class PoolContext {

    /** 目前選中的分類（契約 PoolCategory 的值）；「全部」或還沒點過為 null。 */
    private String selectedCategory;

    public void selectCategory(String categoryKey) {
        this.selectedCategory = "all".equals(categoryKey) ? null : categoryKey;
    }

    public String selectedCategory() {
        return selectedCategory;
    }
}
