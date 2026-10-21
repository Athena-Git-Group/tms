package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 任務的 SOP 勾記：這張表是從 task_events 事件紀錄整理出來、方便查詢的
 * 目前狀態副本，查房退回時歸零重來也沒關係——「誰在幾點勾過什麼」的完整
 * 歷史在 task_events 的 sop_checked 事件，不會因為這張表被覆寫而消失。
 * checked 與 skipped 互斥由 DB CHECK 擋。
 */
@Entity
@Table(name = "task_sop_checks")
@Getter
@Setter
@NoArgsConstructor
public class TaskSopCheck {

    @Id
    private UUID id;

    private Long tenantId;

    private UUID taskId;

    private String itemKey;

    /** 建單當下快照：項目日後改名不影響歷史單。 */
    private String label;

    private boolean checked = false;

    /** 跳過不擋流程但留痕。 */
    private boolean skipped = false;

    private UUID checkedBy;

    private OffsetDateTime checkedAt;
}
