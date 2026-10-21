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
 * 每家飯店自設的 SOP/查房項目（applies_to 區分，同構共表省一張結構相同的表）。
 * tenant_config 的 sop_items / inspection_items 兩鍵刻意不存值、讀寫都轉進本表：
 * config 裡再放一份就是雙真相，遲早漂移（data-model.md 設計決定）。
 * 下架用 active=false 不刪列：task_sop_checks 的 label 快照要能對回來源項目。
 */
@Entity
@Table(name = "sop_items")
@Getter
@Setter
@NoArgsConstructor
public class SopItem {

    @Id
    private UUID id;

    private Long tenantId;

    /** 值域=common.codes.SopAppliesTo。 */
    private String appliesTo;

    private String itemKey;

    private String label;

    private Integer sortOrder;

    private boolean active = true;

    private OffsetDateTime createdAt;
}
