package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * tasks 是從 task_events 事件紀錄整理出來、方便查詢的目前狀態副本（可由
 * task_events 重放重建，權威紀錄在事件表）。本 entity 零業務邏輯：狀態轉移
 * 要用的條件式更新——前提條件（狀態、操作者等）直接寫進 UPDATE 的 WHERE，
 * 不符就一列也不改，event_seq 同句取號——屬任務引擎；這裡只對映欄位。
 * sla_min/esc_level 為保留欄，照 erm.dbml 建欄不賦語意。
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    private UUID id;

    private Long tenantId;

    /** 值域=common.codes.TaskType。 */
    private String type;

    /** 值域=common.codes.TaskSource。 */
    private String source;

    private UUID roomId;

    /** guest 來源必填；住客查詢一律以它過濾（不按房號）。 */
    private UUID reservationId;

    /** 值域=common.codes.Dept（triage 時掛 frontdesk）。 */
    private String dept;

    /** 值域=common.codes.TaskState（九值）。 */
    private String state;

    /** 事件取號器：條件式更新同句 +1 RETURNING（DB DEFAULT 0）。 */
    private int eventSeq = 0;

    /** 池中為空。 */
    private UUID assigneeId;

    private String title;

    /** 住客原文/翻譯/交代——PII 去識別化範圍。 */
    private String note;

    /** 排房帶入。 */
    private Integer estMin;

    /** 查房退回清空重計。 */
    private OffsetDateTime startedAt;

    /** 實際分鐘=done_at−started_at（derived，不落欄）。 */
    private OffsetDateTime doneAt;

    private int redoCount = 0;

    /** 1–5（DB CHECK）；低分觸發老闆推播；一次性。 */
    private Integer rating;

    /** 打掃任務回鏈排房計畫（cleaning_plans 本輪無 entity，純量承載）。 */
    private UUID planId;

    /** 打掃必填（發佈抄自計畫日）——支撐一房一天約束與班末掃描。 */
    private LocalDate workDate;

    /** 保留欄位，SLA 升級鏈啟用前不寫。 */
    private Integer slaMin;

    /** 保留欄位。 */
    private Integer escLevel;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
