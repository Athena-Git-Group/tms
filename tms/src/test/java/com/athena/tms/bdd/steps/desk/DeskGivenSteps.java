package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.DeskContext;

/** 本模組 {@code 櫃檯看板與派單/dsl.md} 的 Given 句型。 */
public class DeskGivenSteps {

    private final ScenarioWorld world;
    private final DeskApi deskApi;
    private final DeskContext deskContext;

    public DeskGivenSteps(ScenarioWorld world, DeskApi deskApi, DeskContext deskContext) {
        this.world = world;
        this.deskApi = deskApi;
        this.deskContext = deskContext;
    }
}
