package com.athena.tms.dao;

import com.athena.tms.entity.StaffDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffDeviceRepository extends JpaRepository<StaffDevice, UUID> {

    Optional<StaffDevice> findByTenantIdAndStaffIdAndDeviceFingerprint(
            Long tenantId, UUID staffId, String deviceFingerprint);

    /** 推播收件地址＝未撤銷的裝置集合。 */
    List<StaffDevice> findByTenantIdAndStaffIdAndRevokedAtIsNull(Long tenantId, UUID staffId);
}
