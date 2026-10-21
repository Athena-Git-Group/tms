package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 租戶（飯店）主檔。id=bigint identity，是「主鍵一律 UUIDv7」慣例的例外，DB 取號。
 * v1 由營運人工開設——tms_app 角色對本表只有 SELECT，應用層視為唯讀。
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private OffsetDateTime createdAt;
}
