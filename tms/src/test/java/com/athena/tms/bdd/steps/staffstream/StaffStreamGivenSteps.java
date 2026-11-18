package com.athena.tms.bdd.steps.staffstream;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.StaffStreamClient;
import com.athena.tms.bdd.support.StreamFaults;
import io.cucumber.java.zh_tw.假設;
import java.time.Duration;

public class StaffStreamGivenSteps {

    static final Duration LAG_OVER_THRESHOLD = Duration.ofSeconds(11);

    private final ScenarioWorld world;
    private final StaffStreamClient streamClient;
    private final StreamFaults streamFaults;

    public StaffStreamGivenSteps(ScenarioWorld world, StaffStreamClient streamClient, StreamFaults streamFaults) {
        this.world = world;
        this.streamClient = streamClient;
        this.streamFaults = streamFaults;
    }

    @假設("{string} 已連上員工事件串流")
    public void connected(String staffName) {
        StaffStreamClient.Connection connection = streamClient.connect(staffName);
        if (connection.status() != 200) {
            throw new IllegalStateException("「" + staffName + "」連不上員工事件串流：" + connection.describe());
        }
        for (String name : new String[] {"init", "resync"}) {
            if (connection.awaitEvent(name, StaffStreamClient.DEFAULT_WAIT).isEmpty()) {
                throw new IllegalStateException("「" + staffName + "」連上串流後 "
                        + StaffStreamClient.DEFAULT_WAIT.toSeconds() + " 秒內沒有收到 " + name + "：" + connection.describe());
            }
        }
    }

    @假設("後端目前 {string}")
    public void backendState(String state) {
        switch (state) {
            case "降級" -> {
                streamFaults.reportLag(LAG_OVER_THRESHOLD);
                streamFaults.awaitProcessed();
            }
            case "正常" -> {
            }
            default -> throw new IllegalArgumentException("未知的後端狀態：" + state + "；只支援 正常、降級");
        }
    }
}
