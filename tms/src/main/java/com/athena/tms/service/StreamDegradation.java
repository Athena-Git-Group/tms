package com.athena.tms.service;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class StreamDegradation {

    public enum Reason {
        CONSUMER_LAG("kafka_consumer_lag"),
        KAFKA_UNAVAILABLE("kafka_unavailable");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        public String code() {
            return this.code;
        }
    }

    private volatile Reason current;
    private final Object lock = new Object();

    public Reason current() {
        return this.current;
    }

    public boolean isDegraded() {
        return this.current != null;
    }

    public <T> T whileLocked(Supplier<T> action) {
        synchronized (this.lock) {
            return action.get();
        }
    }

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

    public boolean leave(Consumer<Reason> announce) {
        return this.leaveWhen(null, announce);
    }

    public boolean leaveIf(Reason reason, Consumer<Reason> announce) {
        return this.leaveWhen(reason, announce);
    }

    private boolean leaveWhen(Reason expected, Consumer<Reason> announce) {
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
