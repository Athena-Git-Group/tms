package com.athena.web.bdd.steps.realtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 「即時更新」的前置與動作句留給結果句的紀錄，一個場景一份。
 *
 * <p>「5 秒內卡片出現在某欄」要從現場那個動作送出的那一刻起算，所以動作句送出前記下時刻。
 *
 * <p>斷線期間畫面應該每 5 秒讀一次看板，「沒有每 5 秒定時讀取」若把那段時間的請求也算進去，就一定判定失敗。
 * 所以「即時連線中斷後已經恢復」的前置在提醒列消失時記下起點，結果句只看起點之後的請求。
 */
public class RealtimeScenario {

    private Instant signalSentAt;

    private Integer boardRequestsFrom;

    private final List<Instant> boardRequestTimes = new ArrayList<>();

    private Boolean alertSeenDuringReconnect;

    /** 記下從切斷串流到重連成功，提醒列有沒有出現過，給「這段期間提醒列一次也沒有出現」比對。 */
    public void markAlertSeenDuringReconnect(boolean seen) {
        alertSeenDuringReconnect = seen;
    }

    /** 切斷到重連成功之間提醒列有沒有出現過；這個場景的動作句沒有監看過時是空的。 */
    public Optional<Boolean> alertSeenDuringReconnect() {
        return Optional.ofNullable(alertSeenDuringReconnect);
    }

    /** 記下現場那個動作送出的時刻：住客送出或收回需求、另一台櫃檯派單。 */
    public void markSignalSent(Instant at) {
        signalSentAt = at;
    }

    /** 現場那個動作送出的時刻；這個場景的動作句不是這一類時是空的。 */
    public Optional<Instant> signalSentAt() {
        return Optional.ofNullable(signalSentAt);
    }

    /** 記下從第幾個請求開始看看板的請求，值是 {@code Network.sentRequestCount()} 當時的值。 */
    public void markBoardRequestsFrom(int requestCount) {
        boardRequestsFrom = requestCount;
    }

    /** 從第幾個請求開始看；前置沒有記過時是空的。 */
    public Optional<Integer> boardRequestsFrom() {
        return Optional.ofNullable(boardRequestsFrom);
    }

    private Boolean resyncSeenAtWhenStart;

    /**
     * 記下前置走完那一刻，後端連上時送的 {@code resync} 是不是已經交給畫面。
     * 還沒到的話，它引起的那一次整份重讀會落在前置之後，「這段期間畫面沒有重新讀取整份看板」要把那一次排除。
     */
    void markResyncSeenAtWhenStart(boolean seen) {
        resyncSeenAtWhenStart = seen;
    }

    /** 前置走完那一刻 {@code resync} 是不是已經到了；那時沒有開著分頁時是空的。 */
    Optional<Boolean> resyncSeenAtWhenStart() {
        return Optional.ofNullable(resyncSeenAtWhenStart);
    }

    void recordBoardRequest(Instant at) {
        boardRequestTimes.add(at);
    }

    /** 從起點之後這個分頁送出看板請求的時刻（真實時間），依先後；前置沒有開始記時是空的。 */
    public List<Instant> boardRequestTimes() {
        return List.copyOf(boardRequestTimes);
    }
}
