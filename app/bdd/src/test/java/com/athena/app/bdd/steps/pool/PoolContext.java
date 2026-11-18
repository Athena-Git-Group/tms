package com.athena.app.bdd.steps.pool;

public class PoolContext {

    private String selectedCategory;

    public void selectCategory(String categoryKey) {
        this.selectedCategory = "all".equals(categoryKey) ? null : categoryKey;
    }

    public String selectedCategory() {
        return selectedCategory;
    }
}
