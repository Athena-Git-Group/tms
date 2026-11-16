package com.athena.tms.bdd.steps.staffstream;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.StaffStreamClient;

/**
 * 本模組 {@code 員工事件串流/dsl.md} 的 Then 句型。
 * 多帶一個 {@link DbProbe}：訊號的 {@code seq} 要和 {@code task_events} 裡那一筆事件對得上。
 */
public class StaffStreamThenSteps {

    private final ScenarioWorld world;
    private final StaffStreamClient streamClient;
    private final DbProbe dbProbe;

    public StaffStreamThenSteps(ScenarioWorld world, StaffStreamClient streamClient, DbProbe dbProbe) {
        this.world = world;
        this.streamClient = streamClient;
        this.dbProbe = dbProbe;
    }
}
