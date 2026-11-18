package com.athena.tms.bdd.steps.staffstream;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.StaffStreamClient;
import com.athena.tms.bdd.support.StreamFaults;
import io.cucumber.java.zh_tw.假設;
import java.time.Duration;

/** 本模組 {@code 員工事件串流/dsl.md} 的 Given 句型。 */
public class StaffStreamGivenSteps {

    /** 模組用語「處理事件落後超過 10 秒」：送一則時間印在 11 秒前的事件，比門檻多一秒才不會落在邊界上。 */
    static final Duration LAG_OVER_THRESHOLD = Duration.ofSeconds(11);

    private final ScenarioWorld world;
    private final StaffStreamClient streamClient;
    private final StreamFaults streamFaults;

    public StaffStreamGivenSteps(ScenarioWorld world, StaffStreamClient streamClient, StreamFaults streamFaults) {
        this.world = world;
        this.streamClient = streamClient;
        this.streamFaults = streamFaults;
    }

    /**
     * 保證後面的步驟開始時，這位員工的連線已經在後端登記好、收得到之後發生的事件。
     * 後端是先送 {@code init} 與 {@code resync} 才算連線就緒，所以兩則都收到才往下走；
     * 不等的話，緊接著的動作可能搶在連線登記之前發生，訊號就永遠收不到。
     */
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

    /**
     * 把後端擺到指定狀態。降級是讓事件消費者看到一則落後超過門檻的事件，並等它處理完，
     * 之後的步驟才能以「後端已在降級」為前提；正常是每個場景開始時的狀態，不必做事。
     */
    @假設("後端目前 {string}")
    public void backendState(String state) {
        switch (state) {
            case "降級" -> {
                streamFaults.reportLag(LAG_OVER_THRESHOLD);
                streamFaults.awaitProcessed();
            }
            case "正常" -> {
                // 場景開始時後端一定是正常的，上一個場景動過的狀態會在它結束時還原。
            }
            default -> throw new IllegalArgumentException("未知的後端狀態：" + state + "；只支援 正常、降級");
        }
    }
}
