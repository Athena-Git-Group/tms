package com.athena.tms.bdd.steps.staffstream;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.StaffStreamClient;
import com.athena.tms.bdd.support.StreamFaults;

/** 本模組 {@code 員工事件串流/dsl.md} 的 Given 句型。 */
public class StaffStreamGivenSteps {

    private final ScenarioWorld world;
    private final StaffStreamClient streamClient;
    private final StreamFaults streamFaults;

    public StaffStreamGivenSteps(ScenarioWorld world, StaffStreamClient streamClient, StreamFaults streamFaults) {
        this.world = world;
        this.streamClient = streamClient;
        this.streamFaults = streamFaults;
    }
}
