package com.athena.tms.bdd.steps.staffstream;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.JobTrigger;
import com.athena.tms.bdd.support.StaffStreamClient;
import com.athena.tms.bdd.support.StreamFaults;

/**
 * 本模組 {@code 員工事件串流/dsl.md} 的 When 句型。
 * 多帶一個 {@link JobTrigger}：「串流過了 15 秒」不能真的等，要靠它的 {@code stream-heartbeat} 立刻觸發一次心跳。
 */
public class StaffStreamWhenSteps {

    private final ScenarioWorld world;
    private final StaffStreamClient streamClient;
    private final StreamFaults streamFaults;
    private final JobTrigger jobTrigger;

    public StaffStreamWhenSteps(
            ScenarioWorld world, StaffStreamClient streamClient, StreamFaults streamFaults, JobTrigger jobTrigger) {
        this.world = world;
        this.streamClient = streamClient;
        this.streamFaults = streamFaults;
        this.jobTrigger = jobTrigger;
    }
}
