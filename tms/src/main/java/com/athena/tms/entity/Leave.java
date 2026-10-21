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
 * 請假。登記當下觸發連鎖（accepted→pool、doing→interrupted）——
 * 效果記事件不回寫本表；連鎖邏輯不在本 entity，屬請假服務。
 */
@Entity
@Table(name = "leaves")
@Getter
@Setter
@NoArgsConstructor
public class Leave {

    @Id
    private UUID id;

    private Long tenantId;

    private UUID staffId;

    private OffsetDateTime fromAt;

    private OffsetDateTime toAt;

    /** 登記的主管。 */
    private UUID createdBy;

    private OffsetDateTime createdAt;
}
