package com.athena.web.bdd.steps.guest.entry;

import com.microsoft.playwright.Page;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 「進入層」的句子之間要傳的東西：換 QR 之前的舊代碼，以及櫃檯 Web 開在哪一個分頁。一個場景一份。
 */
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

    /** 記下櫃檯 Web 登入著的那個分頁；同一個瀏覽器環境裡，住客用的是另一個分頁。 */
    public void rememberDeskTab(Page page) {
        deskTab = page;
    }

    public Optional<Page> deskTab() {
        return deskTab == null || deskTab.isClosed() ? Optional.empty() : Optional.of(deskTab);
    }
}
