package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.DeskContext;

/**
 * 介面根 {@code backend/dsl.md} 裡屬於櫃檯的 5 句（待派單的住客需求、已派出、讀取看板、派單、復原）。
 * 這 5 句會被別的模組的 feature 共用，和本模組專屬的句型分開放，補句型時才不會和另外三個檔互相卡住。
 */
public class DeskCommonSteps {

    private final ScenarioWorld world;
    private final DeskApi deskApi;
    private final DeskContext deskContext;

    public DeskCommonSteps(ScenarioWorld world, DeskApi deskApi, DeskContext deskContext) {
        this.world = world;
        this.deskApi = deskApi;
        this.deskContext = deskContext;
    }
}
