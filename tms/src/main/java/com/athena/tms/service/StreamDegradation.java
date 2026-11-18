package com.athena.tms.service;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 員工事件串流目前是不是降級、為什麼降級。進出降級時的廣播、員工新連線補送的 degraded、心跳帶的旗標都只讀這一個物件，
 * 三者對後端狀態的說法才不會彼此矛盾。
 *
 * <p>進入、離開降級時要對連線送出的訊號，由呼叫端傳進來，在轉換的同一把鎖裡送出；員工新連線送 init、resync 與補送 degraded
 * 也透過 {@link #whileLocked} 拿同一把鎖，所以一條新連線不會在 init 之前先收到廣播的 degraded，也不會同一次降級收到兩則。
 * 只讀目前狀態（例如心跳）不拿鎖。
 */
public final class StreamDegradation {

    /** 降級原因；{@link #code()} 是送給客戶端的原因代碼。 */
    public enum Reason {
        /** 後端處理事件落後超過門檻。 */
        CONSUMER_LAG("kafka_consumer_lag"),
        /** 後端連不上事件來源，或串流的事件消費者停了。 */
        KAFKA_UNAVAILABLE("kafka_unavailable");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        public String code() {
            return this.code;
        }
    }

    /** 目前的降級原因，正常時是 null。只在 {@link #lock} 裡改。 */
    private volatile Reason current;
    private final Object lock = new Object();

    /** 目前的降級原因，正常時回傳 null。 */
    public Reason current() {
        return this.current;
    }

    public boolean isDegraded() {
        return this.current != null;
    }

    /** 新連線要在沒人進出降級的情況下送完開頭的訊號，才不會漏收或重收 degraded，所以拿同一把鎖執行。 */
    public <T> T whileLocked(Supplier<T> action) {
        synchronized (this.lock) {
            return action.get();
        }
    }

    /**
     * 同一次降級只能宣告一次，所以已經在降級時什麼都不做，原因也維持第一次的。
     * {@code announce} 在轉換的同一把鎖裡執行，新連線才不會和這次宣告重複收到 degraded。
     *
     * @return 這次真的從正常轉成降級時回傳 true
     */
    public boolean enter(Reason reason, Consumer<Reason> announce) {
        synchronized (this.lock) {
            if (this.current != null) {
                return false;
            }
            this.current = reason;
            announce.accept(reason);
            return true;
        }
    }

    /**
     * 給不必分辨降級原因的恢復跡象使用，例如及時處理一則事件。
     *
     * @return 這次真的從降級轉回正常時回傳 true
     */
    public boolean leave(Consumer<Reason> announce) {
        return this.leaveWhen(null, announce);
    }

    /**
     * 某些恢復跡象只能解除特定原因的降級，例如事件消費者重新啟動不代表落後已經追上，所以原因不符時什麼都不做。
     *
     * @return 這次真的從降級轉回正常時回傳 true
     */
    public boolean leaveIf(Reason reason, Consumer<Reason> announce) {
        return this.leaveWhen(reason, announce);
    }

    /** {@code expected} 是 null 時不看原因。 */
    private boolean leaveWhen(Reason expected, Consumer<Reason> announce) {
        // 正常時每則事件都會走到這裡，先不拿鎖看一眼，免得每則事件都排隊等鎖。
        if (this.current == null) {
            return false;
        }
        synchronized (this.lock) {
            Reason was = this.current;
            if (was == null || expected != null && expected != was) {
                return false;
            }
            this.current = null;
            announce.accept(was);
            return true;
        }
    }
}
