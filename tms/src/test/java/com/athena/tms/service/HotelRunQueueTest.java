package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.service.HotelRunQueue.TurnOutcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HotelRunQueueTest {

    private static final long A = 1L;
    private static final long B = 2L;
    private static final Duration NO_WAIT = Duration.ZERO;

    private final SteppingClock clock = new SteppingClock();
    private final HotelRunQueue queue = new HotelRunQueue(clock, Duration.ofSeconds(1), Duration.ofSeconds(60));

    @Test
    void aHotelSignalledTwiceIsQueuedOnce() throws Exception {
        queue.signal(A);
        queue.signal(A);

        assertThat(queue.readyCount()).isEqualTo(1);
        assertThat(queue.take(NO_WAIT)).hasValue(A);
        assertThat(queue.take(NO_WAIT)).isEmpty();
    }

    @Test
    void aRunningHotelIsNeverHandedToASecondWorker() throws Exception {
        queue.signal(A);
        queue.take(NO_WAIT);

        queue.signal(A);

        assertThat(queue.take(NO_WAIT)).as("同一家飯店同時只給一個執行緒").isEmpty();
    }

    @Test
    void aSignalDuringATurnRunsTheHotelOnceMoreAfterIt() throws Exception {
        queue.signal(A);
        queue.take(NO_WAIT);
        queue.signal(A);
        queue.signal(A);

        queue.finish(A, TurnOutcome.DRAINED);

        assertThat(queue.take(NO_WAIT)).hasValue(A);
        queue.finish(A, TurnOutcome.DRAINED);
        assertThat(queue.take(NO_WAIT)).as("一輪期間的多次喚醒只換來一輪").isEmpty();
    }

    @Test
    void aHotelWithMoreWorkGoesBehindHotelsThatBecameReady() throws Exception {
        queue.signal(A);
        queue.signal(B);
        List<Long> order = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            long hotel = queue.take(NO_WAIT).orElseThrow();
            order.add(hotel);
            queue.finish(hotel, TurnOutcome.MORE);
        }

        assertThat(order).containsExactly(A, B, A, B);
    }

    @Test
    void aFailingHotelIsParkedWithDoublingCooldownUpToTheCap() throws Exception {
        List<Duration> cooldowns = new ArrayList<>();
        queue.signal(A);
        for (int i = 0; i < 8; i++) {
            queue.take(NO_WAIT).orElseThrow();
            queue.finish(A, TurnOutcome.FAILED);
            Duration waited = Duration.ZERO;
            while (queue.readyCount() == 0) {
                clock.advance(Duration.ofMillis(500));
                waited = waited.plusMillis(500);
                queue.signal(A);
            }
            cooldowns.add(waited);
        }

        assertThat(cooldowns).containsExactly(
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8),
                Duration.ofSeconds(16), Duration.ofSeconds(32), Duration.ofSeconds(60), Duration.ofSeconds(60));
    }

    @Test
    void aCleanTurnResetsTheCooldown() throws Exception {
        queue.signal(A);
        for (int i = 0; i < 3; i++) {
            queue.take(NO_WAIT).orElseThrow();
            queue.finish(A, TurnOutcome.FAILED);
            clock.advance(Duration.ofMinutes(5));
            queue.signal(A);
        }
        queue.take(NO_WAIT).orElseThrow();
        queue.finish(A, TurnOutcome.MORE);
        queue.take(NO_WAIT).orElseThrow();
        queue.finish(A, TurnOutcome.FAILED);

        clock.advance(Duration.ofSeconds(1));
        queue.signal(A);

        assertThat(queue.take(NO_WAIT)).hasValue(A);
    }

    @Test
    void aParkedHotelIgnoresSignalsUntilItsCooldownEnds() throws Exception {
        queue.signal(A);
        queue.take(NO_WAIT);
        queue.finish(A, TurnOutcome.FAILED);

        clock.advance(Duration.ofMillis(900));
        queue.signal(A);
        assertThat(queue.take(NO_WAIT)).isEmpty();

        clock.advance(Duration.ofMillis(100));
        queue.signal(A);
        assertThat(queue.take(NO_WAIT)).hasValue(A);
    }

    @Test
    void closingWakesWaitingWorkersEmptyHanded() throws Exception {
        Thread closer = new Thread(() -> {
            sleep(100);
            queue.close();
        });
        closer.start();

        long started = System.nanoTime();
        OptionalLong taken = queue.take(Duration.ofSeconds(10));

        assertThat(taken).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void randomOperationsNeverHandOutARunningHotelOrQueueOneTwice() throws Exception {
        Random random = new Random(42);
        Set<Long> running = new HashSet<>();
        for (int step = 0; step < 20_000; step++) {
            long hotel = 1 + random.nextInt(5);
            switch (random.nextInt(4)) {
                case 0, 1 -> queue.signal(hotel);
                case 2 -> {
                    OptionalLong taken = queue.take(NO_WAIT);
                    if (taken.isPresent()) {
                        assertThat(running.add(taken.getAsLong())).as("步驟 %d：飯店 %d 已在執行中又被取出", step, taken.getAsLong()).isTrue();
                    }
                }
                default -> {
                    if (!running.isEmpty()) {
                        long done = running.iterator().next();
                        running.remove(done);
                        queue.finish(done, TurnOutcome.values()[random.nextInt(3)]);
                        clock.advance(Duration.ofMillis(random.nextInt(3000)));
                    }
                }
            }
            assertThat(queue.readyCount() + running.size()).as("步驟 %d：同一家飯店不會同時排隊又執行", step).isLessThanOrEqualTo(5);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class SteppingClock extends Clock {
        private Instant now = Instant.parse("2026-11-26T00:00:00Z");

        void advance(Duration step) {
            now = now.plus(step);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
