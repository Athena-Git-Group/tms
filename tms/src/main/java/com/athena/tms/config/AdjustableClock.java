package com.athena.tms.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public final class AdjustableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant fixedInstant;

    public AdjustableClock() {
        this(ZoneId.systemDefault());
    }

    public AdjustableClock(ZoneId zone) {
        this.zone = zone;
    }

    public void fixAt(Instant instant) {
        this.fixedInstant = instant;
    }

    public void reset() {
        this.fixedInstant = null;
    }

    public boolean isFixed() {
        return fixedInstant != null;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        if (newZone.equals(zone)) {
            return this;
        }
        AdjustableClock parent = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return newZone;
            }

            @Override
            public Clock withZone(ZoneId z) {
                return parent.withZone(z);
            }

            @Override
            public Instant instant() {
                return parent.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        Instant fixed = fixedInstant;
        return fixed != null ? fixed : Instant.now();
    }
}
