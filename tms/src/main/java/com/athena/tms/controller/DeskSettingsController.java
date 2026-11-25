package com.athena.tms.controller;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dto.desk.DeskSettings;
import com.athena.tms.service.ManagerReviewSettings;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/desk/settings")
public class DeskSettingsController {

    private static final String INSPECTION_REQUIRED = "inspectionRequired";
    private static final String REPAIR_VERIFICATION_REQUIRED = "repairVerificationRequired";

    private final ManagerReviewSettings managerReviewSettings;

    public DeskSettingsController(ManagerReviewSettings managerReviewSettings) {
        this.managerReviewSettings = managerReviewSettings;
    }

    @GetMapping
    public DeskSettings getDeskSettings() {
        return managerReviewSettings.read(TenantContext.require());
    }

    @PatchMapping
    @Transactional
    public DeskSettings patchDeskSettings(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> patch = body == null ? Map.of() : body;
        if (!patch.containsKey(INSPECTION_REQUIRED) && !patch.containsKey(REPAIR_VERIFICATION_REQUIRED)) {
            throw new UnprocessableEntityException("至少要帶 inspectionRequired 或 repairVerificationRequired 其中一個");
        }
        long tenantId = TenantContext.require();
        managerReviewSettings.update(tenantId, CurrentActor.require().staffId(),
                switchValue(patch, INSPECTION_REQUIRED), switchValue(patch, REPAIR_VERIFICATION_REQUIRED));
        return managerReviewSettings.read(tenantId);
    }

    private static Boolean switchValue(Map<String, Object> patch, String field) {
        if (!patch.containsKey(field)) {
            return null;
        }
        if (patch.get(field) instanceof Boolean value) {
            return value;
        }
        throw new UnprocessableEntityException(field + " 必須是 true 或 false");
    }
}
