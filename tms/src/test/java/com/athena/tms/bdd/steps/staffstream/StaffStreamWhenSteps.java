package com.athena.tms.bdd.steps.staffstream;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.JobTrigger;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.StaffStreamClient;
import com.athena.tms.bdd.support.StreamFaults;
import io.cucumber.java.zh_tw.當;
import java.time.Duration;
import java.util.Map;

public class StaffStreamWhenSteps {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final ScenarioWorld world;
    private final StaffStreamClient streamClient;
    private final StreamFaults streamFaults;
    private final JobTrigger jobTrigger;
    private final MutableClock clock;
    private final BddApiClient apiClient;
    private final DeskApi deskApi;

    public StaffStreamWhenSteps(
            ScenarioWorld world,
            StaffStreamClient streamClient,
            StreamFaults streamFaults,
            JobTrigger jobTrigger,
            MutableClock clock,
            BddApiClient apiClient,
            DeskApi deskApi) {
        this.world = world;
        this.streamClient = streamClient;
        this.streamFaults = streamFaults;
        this.jobTrigger = jobTrigger;
        this.clock = clock;
        this.apiClient = apiClient;
        this.deskApi = deskApi;
    }

    /** 住客工作階段掛在這間房現在住著的那筆訂房上，和單上連結的是同一筆，評分才認得這張單。 */
    @當("{string} 的住客給了評分")
    public void guestRates(String roomNo) {
        String path = "/guest/requests/" + world.taskId(roomNo) + "/rating";
        ScenarioWorld.ApiResponse response = apiClient.postUnversioned(
                path, BddApiClient.Auth.guest(deskApi.guestSessionToken(roomNo)), Map.of("rating", 5));
        if (response.status() < 200 || response.status() > 299) {
            throw new IllegalStateException(roomNo + " 的住客評分失敗，狀態碼=" + response.status() + "，body=" + response.body());
        }
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
