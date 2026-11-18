package com.athena.tms.service;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.desk.DeskQuickReply;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 當場回答面板的常用回覆：就是飯店住客資訊頁的每一頁，照飯店設定的順序。
 *
 * <p>和住客首頁讀同一份設定、同一個讀法，所以兩邊看到的頁數、順序與文字一致。飯店沒有設定時回空清單。
 */
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
