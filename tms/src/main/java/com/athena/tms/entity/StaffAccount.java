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
 * 員工帳號。PK=UUIDv7 應用層產號（common.UuidV7）。
 * 停用=active=false 不刪列（歷史單追責）；name/login_id 為 PII（去識別化範圍）。
 * 角色×動作矩陣在程式/設定，不在表裡。
 */
@Entity
@Table(name = "staff_accounts")
@Getter
@Setter
@NoArgsConstructor
public class StaffAccount {

    @Id
    private UUID id;

    private Long tenantId;

    private String name;

    private String loginId;

    /** bcrypt/argon2，絕不存明文。 */
    private String passwordHash;

    /** 值域=common.codes.StaffRole。 */
    private String role;

    /** 值域=common.codes.Dept；主管/老闆可空。 */
    private String dept;

    private boolean active = true;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
