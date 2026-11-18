package com.athena.tms.bdd.steps.staffstream;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.JobTrigger;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.StaffStreamClient;
import com.athena.tms.bdd.support.StreamFaults;
import io.cucumber.java.zh_tw.當;
import java.time.Duration;

/**
 * 本模組 {@code 員工事件串流/dsl.md} 的 When 句型。
 * 多帶一個 {@link JobTrigger}：「串流過了 15 秒」不能真的等，要靠它的 {@code stream-heartbeat} 立刻觸發一次心跳。
 */
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

    /** 連上或被拒絕都照樣記下，由 Then 判斷結果；所以這裡不等任何訊號。 */
    @當("{string} 連上員工事件串流")
    public void connect(String staffName) {
        streamClient.connect(staffName);
    }

    /** 等事件消費者處理完才往下走，Then 檢查時後端一定已經算過這次落後。 */
    @當("後端處理事件落後超過 10 秒")
    public void consumerLagsBehind() {
        streamFaults.reportLag(StaffStreamGivenSteps.LAG_OVER_THRESHOLD);
        streamFaults.awaitProcessed();
    }

    /**
     * 停掉事件消費者。消費者停著時無法用「處理完了沒」來確認後端已經察覺，
     * 所以這裡停完就回來，後端多久宣告降級由 Then 的等待上限來容納。
     */
    @當("後端連不上事件來源")
    public void eventSourceUnavailable() {
        streamFaults.disconnectEventSource();
    }

    /** 送一則時間是現在的事件並等它處理完，Then 檢查時後端一定已經算出落後歸零。 */
    @當("後端處理事件回到正常")
    public void consumerCatchesUp() {
        streamFaults.reportCaughtUp();
        streamFaults.awaitProcessed();
    }

    /**
     * 讓「過了 15 秒」在當下發生：把測試時鐘往後撥 15 秒，再立刻觸發一次心跳。
     * 後端真實的定時心跳仍會照跑，Then 不能假設連線上只有這一則心跳。
     */
    @當("串流過了 15 秒")
    public void fifteenSecondsPass() {
        clock.setInstant(clock.instant().plus(HEARTBEAT_INTERVAL));
        jobTrigger.run("stream-heartbeat");
    }
}
