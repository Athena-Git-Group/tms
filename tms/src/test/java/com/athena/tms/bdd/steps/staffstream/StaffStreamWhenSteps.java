package com.athena.tms.bdd.steps.staffstream;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.JobTrigger;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.StaffStreamClient;
import com.athena.tms.bdd.support.StreamFaults;
import io.cucumber.java.zh_tw.當;
import java.time.Duration;

public class StaffStreamWhenSteps {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final ScenarioWorld world;
    private final StaffStreamClient streamClient;
    private final StreamFaults streamFaults;
    private final JobTrigger jobTrigger;
    private final MutableClock clock;

    public StaffStreamWhenSteps(
            ScenarioWorld world,
            StaffStreamClient streamClient,
            StreamFaults streamFaults,
            JobTrigger jobTrigger,
            MutableClock clock) {
        this.world = world;
        this.streamClient = streamClient;
        this.streamFaults = streamFaults;
        this.jobTrigger = jobTrigger;
        this.clock = clock;
    }

    @當("{string} 連上員工事件串流")
    public void connect(String staffName) {
        streamClient.connect(staffName);
    }

    @當("後端處理事件落後超過 10 秒")
    public void consumerLagsBehind() {
        streamFaults.reportLag(StaffStreamGivenSteps.LAG_OVER_THRESHOLD);
        streamFaults.awaitProcessed();
    }

    @當("後端連不上事件來源")
    public void eventSourceUnavailable() {
        streamFaults.disconnectEventSource();
    }

    @當("後端處理事件回到正常")
    public void consumerCatchesUp() {
        streamFaults.reportCaughtUp();
        streamFaults.awaitProcessed();
    }

    @當("串流過了 15 秒")
    public void fifteenSecondsPass() {
        clock.setInstant(clock.instant().plus(HEARTBEAT_INTERVAL));
        jobTrigger.run("stream-heartbeat");
    }
}
