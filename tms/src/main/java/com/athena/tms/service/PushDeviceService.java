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

/**
 * 員工本人的推播地址簿（staff_devices）維護：登記與解除。
 *
 * <p>一台手機一列，以 deviceFingerprint 認手機。重複登記同一台只更新推播地址與平台、並把
 * 撤銷標記清掉；若每次登記都新增一列，同一台手機會留下一串舊地址，推播消費者對每一列都送
 * 一次，員工同一支手機會響好幾次。解除是標 revoked_at 而不是刪列，保留這台手機曾登記的痕跡，
 * 也讓推播服務回報地址失效時有同一個欄位可以標。
 */
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

    /** 沒有這台手機的登記也回成功：登出的目的是「之後不要再送」，本來就沒登記等於已達成。 */
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
