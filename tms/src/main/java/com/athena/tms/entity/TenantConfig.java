/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.persistence.Entity
 *  jakarta.persistence.Id
 *  jakarta.persistence.IdClass
 *  jakarta.persistence.Table
 *  lombok.Generated
 *  org.hibernate.annotations.JdbcTypeCode
 */
package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name="tenant_config")
@IdClass(value=TenantConfig.Pk.class)
public class TenantConfig {
    @Id
    private Long tenantId;
    @Id
    private String key;
    @JdbcTypeCode(value=3001)
    private String value;
    private OffsetDateTime updatedAt;
    private UUID updatedBy;

    @Generated
    public Long getTenantId() {
        return this.tenantId;
    }

    @Generated
    public String getKey() {
        return this.key;
    }

    @Generated
    public String getValue() {
        return this.value;
    }

    @Generated
    public OffsetDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    @Generated
    public UUID getUpdatedBy() {
        return this.updatedBy;
    }

    @Generated
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    @Generated
    public void setKey(String key) {
        this.key = key;
    }

    @Generated
    public void setValue(String value) {
        this.value = value;
    }

    @Generated
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Generated
    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    @Generated
    public TenantConfig() {
    }

    public static class Pk
    implements Serializable {
        private Long tenantId;
        private String key;

        @Generated
        public Long getTenantId() {
            return this.tenantId;
        }

        @Generated
        public String getKey() {
            return this.key;
        }

        @Generated
        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        @Generated
        public void setKey(String key) {
            this.key = key;
        }

        @Generated
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof Pk)) {
                return false;
            }
            Pk other = (Pk)o;
            if (!other.canEqual(this)) {
                return false;
            }
            Long this$tenantId = this.getTenantId();
            Long other$tenantId = other.getTenantId();
            if (this$tenantId == null ? other$tenantId != null : !((Object)this$tenantId).equals(other$tenantId)) {
                return false;
            }
            String this$key = this.getKey();
            String other$key = other.getKey();
            return !(this$key == null ? other$key != null : !this$key.equals(other$key));
        }

        @Generated
        protected boolean canEqual(Object other) {
            return other instanceof Pk;
        }

        @Generated
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Long $tenantId = this.getTenantId();
            result = result * 59 + ($tenantId == null ? 43 : ((Object)$tenantId).hashCode());
            String $key = this.getKey();
            result = result * 59 + ($key == null ? 43 : $key.hashCode());
            return result;
        }

        @Generated
        public String toString() {
            return "TenantConfig.Pk(tenantId=" + this.getTenantId() + ", key=" + this.getKey() + ")";
        }

        @Generated
        public Pk() {
        }

        @Generated
        public Pk(Long tenantId, String key) {
            this.tenantId = tenantId;
            this.key = key;
        }
    }
}
