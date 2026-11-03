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
import lombok.Generated;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name="processed_ops")
@IdClass(value=ProcessedOp.Pk.class)
public class ProcessedOp {
    @Id
    private Long tenantId;
    @Id
    private String opId;
    @JdbcTypeCode(value=3001)
    private String resultRef;
    private OffsetDateTime createdAt;

    @Generated
    public Long getTenantId() {
        return this.tenantId;
    }

    @Generated
    public String getOpId() {
        return this.opId;
    }

    @Generated
    public String getResultRef() {
        return this.resultRef;
    }

    @Generated
    public OffsetDateTime getCreatedAt() {
        return this.createdAt;
    }

    @Generated
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    @Generated
    public void setOpId(String opId) {
        this.opId = opId;
    }

    @Generated
    public void setResultRef(String resultRef) {
        this.resultRef = resultRef;
    }

    @Generated
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Generated
    public ProcessedOp() {
    }

    public static class Pk
    implements Serializable {
        private Long tenantId;
        private String opId;

        @Generated
        public Long getTenantId() {
            return this.tenantId;
        }

        @Generated
        public String getOpId() {
            return this.opId;
        }

        @Generated
        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        @Generated
        public void setOpId(String opId) {
            this.opId = opId;
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
            String this$opId = this.getOpId();
            String other$opId = other.getOpId();
            return !(this$opId == null ? other$opId != null : !this$opId.equals(other$opId));
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
            String $opId = this.getOpId();
            result = result * 59 + ($opId == null ? 43 : $opId.hashCode());
            return result;
        }

        @Generated
        public String toString() {
            return "ProcessedOp.Pk(tenantId=" + this.getTenantId() + ", opId=" + this.getOpId() + ")";
        }

        @Generated
        public Pk() {
        }

        @Generated
        public Pk(Long tenantId, String opId) {
            this.tenantId = tenantId;
            this.opId = opId;
        }
    }
}
