package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 查房記錄（history）：一次查房一列、退回重查多列。
 * 「inspector ≠ 原清房者」是跨表規則，由查房服務的程式層擋成 403。
 */
@Entity
@Table(name = "inspections")
@Getter
@Setter
@NoArgsConstructor
public class Inspection {

    @Id
    private UUID id;

    private Long tenantId;

    private UUID taskId;

    private UUID inspectorId;

    /** 值域=common.codes.InspectionResult。 */
    private String result;

    /** jsonb [{key, pass}]；鍵集=租戶查房項目全集（缺項 422，程式層擋）。 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String items;

    private OffsetDateTime createdAt;
}
