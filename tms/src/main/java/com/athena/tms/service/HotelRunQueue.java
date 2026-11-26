package com.athena.tms.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * relay 的租戶排班表，讓一個積壓或壞掉的租戶拖不慢其他租戶。
 *
 * <p>這個類別保證兩件事。第一，一個租戶積壓再多，別的租戶新提交的訊息最多只等排在前面的每個租戶各送一輪。
 * 第二，同一個租戶在這個 instance 上同時只有一個執行緒在送，同一個隊伍的訊息不會被兩個執行緒搶著送。
 *
 * <p>為了第一點，每輪的送出量有上限，送完還有剩的租戶要排到列尾，讓等待中的租戶先輪。
 * 為了不讓壞掉的租戶一直佔住執行緒，一輪拋出例外的租戶會暫停一段時間，暫停時間每次加倍，
 * 第一輪順利做完就歸零。暫停期間的喚醒會被忽略，暫停結束後要等下一次喚醒才會重新排入。
 */
final class HotelRunQueue {

    enum TurnOutcome { DRAINED, MORE, FAILED }

    private sealed interface Slot permits Queued, Running, Parked {
    }

    private record Queued(int failures) implements Slot {
    }

    private record Running(boolean dirty, int failures) implements Slot {
    }

    private record Parked(Instant until, int failures) implements Slot {
    }

    private final Clock clock;
    private final Duration cooldownBase;
    private final Duration cooldownMax;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition readyNotEmpty = lock.newCondition();
    private final Map<Long, Slot> slots = new HashMap<>();
    private final ArrayDeque<Long> ready = new ArrayDeque<>();
    private boolean closed;

    HotelRunQueue(Clock clock, Duration cooldownBase, Duration cooldownMax) {
        this.clock = clock;
        this.cooldownBase = cooldownBase;
        this.cooldownMax = cooldownMax;
    }

    void signal(long tenantId) {
        lock.lock();
        try {
            switch (slots.get(tenantId)) {
                case null -> enqueue(tenantId, 0);
                case Queued queued -> {
                }
                case Running running -> slots.put(tenantId, new Running(true, running.failures()));
                case Parked parked -> {
                    if (!clock.instant().isBefore(parked.until())) {
                        enqueue(tenantId, parked.failures());
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    OptionalLong take(Duration timeout) throws InterruptedException {
        long remaining = timeout.toNanos();
        lock.lock();
        try {
            while (ready.isEmpty() && !closed) {
                if (remaining <= 0) {
                    return OptionalLong.empty();
                }
                remaining = readyNotEmpty.awaitNanos(remaining);
            }
            if (closed) {
                return OptionalLong.empty();
            }
            long tenantId = ready.removeFirst();
            Queued queued = (Queued) slots.get(tenantId);
            slots.put(tenantId, new Running(false, queued.failures()));
            return OptionalLong.of(tenantId);
        } finally {
            lock.unlock();
        }
    }

    void finish(long tenantId, TurnOutcome outcome) {
        lock.lock();
        try {
            Running running = (Running) slots.get(tenantId);
            switch (outcome) {
                case FAILED -> slots.put(tenantId,
                        new Parked(clock.instant().plus(cooldown(running.failures())), running.failures() + 1));
                case MORE -> enqueue(tenantId, 0);
                case DRAINED -> {
                    if (running.dirty()) {
                        enqueue(tenantId, 0);
                    } else {
                        slots.remove(tenantId);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    void close() {
        lock.lock();
        try {
            closed = true;
            readyNotEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    int readyCount() {
        lock.lock();
        try {
            return ready.size();
        } finally {
            lock.unlock();
        }
    }

    private void enqueue(long tenantId, int failures) {
        slots.put(tenantId, new Queued(failures));
        ready.addLast(tenantId);
        readyNotEmpty.signal();
    }

    private Duration cooldown(int failures) {
        Duration doubled = cooldownBase.multipliedBy(1L << Math.min(failures, 20));
        return doubled.compareTo(cooldownMax) > 0 ? cooldownMax : doubled;
    }
}
