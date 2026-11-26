package com.athena.tms.service;

import com.athena.tms.service.HotelRunQueue.TurnOutcome;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 把 outbox 的訊息送上 Kafka，並且讓每個租戶的送出互不拖累。
 *
 * <p>提交寫了 outbox 的交易後，只喚醒那一個租戶，讓看板的即時更新不必等定時輪詢。
 * 定時補漏只問資料庫哪幾個租戶有到期的訊息，再喚醒那幾個，用來接手重試、
 * 當掉的 instance 留下的租約，以及別的 instance 提交的訊息。
 * 固定數量的工作執行緒依 {@link HotelRunQueue} 輪流服務各個租戶，每輪的送出由 {@link HotelRelayTurn} 負責。
 */
@Component
@EnableConfigurationProperties(RelaySettings.class)
public class OutboxRelay implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final Duration COOLDOWN_BASE = Duration.ofSeconds(1);
    private static final Duration IDLE_WAIT = Duration.ofSeconds(1);

    private final HotelRelayTurn turn;
    private final TenantScopedExecutor tenants;
    private final OutboxRelayMetrics metrics;
    private final RelaySettings settings;
    private volatile HotelRunQueue queue;
    private volatile boolean running;
    private List<Thread> workers = List.of();
    private Thread tick;

    OutboxRelay(HotelRelayTurn turn, TenantScopedExecutor tenants, OutboxRelayMetrics metrics, RelaySettings settings) {
        this.turn = turn;
        this.tenants = tenants;
        this.metrics = metrics;
        this.settings = settings;
        this.queue = newQueue();
        metrics.readyHotels(() -> this.queue.readyCount());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEnqueued(OutboxEnqueued event) {
        queue.signal(event.tenantId());
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        HotelRunQueue current = queue;
        running = true;
        List<Thread> started = new ArrayList<>();
        for (int i = 0; i < settings.workers(); i++) {
            started.add(daemon("outbox-relay-" + i, () -> work(current)));
        }
        workers = started;
        tick = daemon("outbox-relay-tick", () -> tick(current));
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        HotelRunQueue stopping = queue;
        queue = newQueue();
        stopping.close();
        tick.interrupt();
        long deadline = System.nanoTime() + settings.sendTimeout().plusSeconds(2).toNanos();
        for (Thread worker : workers) {
            try {
                worker.join(Math.max(1, (deadline - System.nanoTime()) / 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (worker.isAlive()) {
                worker.interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void work(HotelRunQueue current) {
        while (running && queue == current) {
            OptionalLong next;
            try {
                next = current.take(IDLE_WAIT);
            } catch (InterruptedException e) {
                return;
            }
            if (next.isEmpty()) {
                continue;
            }
            long tenantId = next.getAsLong();
            long started = System.nanoTime();
            TurnOutcome outcome = TurnOutcome.FAILED;
            try {
                outcome = turn.run(tenantId, () -> running);
            } catch (Throwable e) {
                log.error("租戶 {} 這輪 relay 出錯，這個租戶暫停一段時間再試，其他租戶照常: {}", tenantId, e.getMessage(), e);
            }
            current.finish(tenantId, outcome);
            try {
                metrics.turn(tenantId, outcome, Duration.ofNanos(System.nanoTime() - started));
            } catch (RuntimeException e) {
                log.warn("relay 指標記錄失敗，不影響送出: {}", e.getMessage(), e);
            }
        }
    }

    private void tick(HotelRunQueue current) {
        while (running && queue == current) {
            try {
                tenants.tenantsWithDueOutbox().forEach(current::signal);
            } catch (RuntimeException e) {
                log.warn("relay 定時補漏查不到有待送訊息的租戶，下一輪再試: {}", e.getMessage());
            }
            try {
                Thread.sleep(settings.pollInterval());
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private HotelRunQueue newQueue() {
        return new HotelRunQueue(Clock.systemUTC(), COOLDOWN_BASE, settings.hotelCooldownMax());
    }

    private static Thread daemon(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
