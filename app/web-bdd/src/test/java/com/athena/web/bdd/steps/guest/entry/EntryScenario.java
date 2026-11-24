package com.athena.web.bdd.steps.guest.entry;

import com.microsoft.playwright.Page;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class EntryScenario {

    private final Map<String, String> oldQrTokenByRoom = new LinkedHashMap<>();

    private Page deskTab;

    public void rememberOldQrToken(String roomNo, String token) {
        oldQrTokenByRoom.put(roomNo, token);
    }

    public String oldQrToken(String roomNo) {
        String token = oldQrTokenByRoom.get(roomNo);
        if (token == null) {
            throw new IllegalStateException("這個場景沒有換過 " + roomNo + " 房的 QR Code，沒有舊代碼");
        }
        return token;
    }

    public void rememberDeskTab(Page page) {
        deskTab = page;
    }

    public Optional<Page> deskTab() {
        return deskTab == null || deskTab.isClosed() ? Optional.empty() : Optional.of(deskTab);
    }
}
