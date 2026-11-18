package com.athena.tms.service;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.desk.DeskQuickReply;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DeskQuickReplyService {
    private final GuestTenantConfigReader configReader;

    public DeskQuickReplyService(GuestTenantConfigReader configReader) {
        this.configReader = configReader;
    }

    public List<DeskQuickReply> list() {
        long tenantId = TenantContext.require();
        return this.configReader.infoPages(tenantId).stream()
                .map(page -> new DeskQuickReply(page.key(), page.title(), page.body()))
                .toList();
    }
}
