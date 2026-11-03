package com.athena.tms.bdd.support;

import com.athena.tms.service.IdempotencyRetentionJob;
import com.athena.tms.service.OutboxRelay;
import com.athena.tms.service.OutboxRetentionJob;
import com.athena.tms.service.StreamHeartbeat;
import java.util.Set;
import org.springframework.context.ApplicationContext;

/**
 * BDD 情境用的排程觸發器：不等 {@code @Scheduled} 真正的排程間隔跑到，而是依名稱
 * 同步呼叫對應 job 的方法。因為呼叫是同步的，{@link #run(String)} 回傳時該次執行
 * 就已完成，不需要額外等待或輪詢。
 *
 * <p>擴充點：之後新增排程 job（例如班末掃描、提醒通知）時，在 {@link #run(String)}
 * 的 switch 加一個對應 case 即可，不需要改動這個類別的其他部分。
 */
public class JobTrigger {

    private static final Set<String> KNOWN_JOBS = Set.of(
            "outbox-relay", "outbox-retention", "idempotency-retention", "stream-heartbeat", "escalation-scan", "shift-end-scan", "schedule-rule-scan");

    private final ApplicationContext applicationContext;

    public JobTrigger(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 依名稱同步觸發一個排程工作，方法回傳時代表該次執行已完成。
     *
     * @param jobName 已知名稱：{@code outbox-relay}、{@code outbox-retention}、
     *                {@code idempotency-retention}、{@code stream-heartbeat}、
     *                {@code escalation-scan}
     * @throws IllegalArgumentException 名稱不在已知清單時，訊息會列出目前已知的名稱
     * @throws UnsupportedOperationException {@code escalation-scan}：main 尚未建立這個排程
     *                （見 dsl.md「時間來到」列「排程建單、逾時升級」的週期掃描），見方法內註解
     */
    public void run(String jobName) {
        switch (jobName) {
            case "outbox-relay" -> applicationContext.getBean(OutboxRelay.class).poll();
            case "outbox-retention" -> applicationContext.getBean(OutboxRetentionJob.class).purgeSentMessages();
            case "idempotency-retention" ->
                    applicationContext.getBean(IdempotencyRetentionJob.class).purgeExpiredOps();
            case "stream-heartbeat" -> applicationContext.getBean(StreamHeartbeat.class).beat();
            case "shift-end-scan" -> throw new UnsupportedOperationException(
                    "main 尚未建立班末掃描 job，等 Feature GREEN 建立後接上");
            case "schedule-rule-scan" -> throw new UnsupportedOperationException(
                    "main 尚未建立自動化規則排程掃描 job，等 Feature GREEN 建立後接上");
            case "escalation-scan" -> throw new UnsupportedOperationException(
                    "main 尚未建立逾時升級與排程建單的週期掃描 job（dsl.md「時間來到」列點名的"
                            + "「排程建單、逾時升級」），等 Feature GREEN 建立對應的 @Scheduled job 後再接上。");
            default -> throw new IllegalArgumentException(
                    "未知的排程工作名稱：" + jobName + "；已知名稱：" + KNOWN_JOBS);
        }
    }
}
