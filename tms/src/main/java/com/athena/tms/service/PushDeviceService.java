package com.athena.tms.service;

import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.StaffDeviceRepository;
import com.athena.tms.dto.PushTokenRegisterRequest;
import com.athena.tms.entity.StaffDevice;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushDeviceService {

    private static final Set<String> PLATFORMS = Set.of("ios", "android");

    private final StaffDeviceRepository staffDeviceRepository;
    private final Clock clock;

    public PushDeviceService(StaffDeviceRepository staffDeviceRepository, Clock clock) {
        this.staffDeviceRepository = staffDeviceRepository;
        this.clock = clock;
    }

    @Transactional
    public void register(long tenantId, UUID staffId, PushTokenRegisterRequest request) {
        String fingerprint = requireText(request == null ? null : request.deviceFingerprint(), "deviceFingerprint");
        String pushToken = requireText(request.pushToken(), "pushToken");
        String platform = requireText(request.platform(), "platform").toLowerCase();
        if (!PLATFORMS.contains(platform)) {
            throw new UnprocessableEntityException("platform 只接受 ios 或 android");
        }
        StaffDevice device = staffDeviceRepository
                .findByTenantIdAndStaffIdAndDeviceFingerprint(tenantId, staffId, fingerprint)
                .orElseGet(() -> {
                    StaffDevice created = new StaffDevice();
                    created.setId(UuidV7.generate());
                    created.setTenantId(tenantId);
                    created.setStaffId(staffId);
                    created.setDeviceFingerprint(fingerprint);
                    created.setRegisteredAt(OffsetDateTime.now(clock));
                    return created;
                });
        device.setPushToken(pushToken);
        device.setPlatform(platform);
        device.setRevokedAt(null);
        staffDeviceRepository.save(device);
    }

    @Transactional
    public void revoke(long tenantId, UUID staffId, String deviceFingerprint) {
        String fingerprint = requireText(deviceFingerprint, "deviceFingerprint");
        staffDeviceRepository
                .findByTenantIdAndStaffIdAndDeviceFingerprint(tenantId, staffId, fingerprint)
                .ifPresent(device -> {
                    if (device.getRevokedAt() == null) {
                        device.setRevokedAt(OffsetDateTime.now(clock));
                        staffDeviceRepository.save(device);
                    }
                });
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new UnprocessableEntityException(field + " 必填");
        }
        return value.trim();
    }
}
