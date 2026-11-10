package com.athena.tms.dao;

import com.athena.tms.entity.StaffDevice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffDeviceRepository
extends JpaRepository<StaffDevice, UUID> {
    public Optional<StaffDevice> findByTenantIdAndStaffIdAndDeviceFingerprint(Long var1, UUID var2, String var3);

    public List<StaffDevice> findByTenantIdAndStaffIdAndRevokedAtIsNull(Long var1, UUID var2);
}
