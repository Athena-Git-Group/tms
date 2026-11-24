package com.athena.web.bdd.steps.guest.guide;

import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class InfoPages {

    private static final String CONFIG_KEY = "guest_info_pages";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebWorld world;

    InfoPages(WebWorld world) {
        this.world = world;
    }

    static Map<String, String> page(String key, String title, String body) {
        Map<String, String> page = new LinkedHashMap<>();
        page.put("key", key);
        page.put("title", title);
        page.put("body", body);
        return page;
    }

    List<Map<String, String>> read() {
        List<Map<String, Object>> rows = world.backend().queryRows(
                "SELECT value::text AS value FROM tenant_config WHERE tenant_id = ? AND key = ?",
                world.defaults().tenantId(), CONFIG_KEY);
        if (rows.isEmpty()) {
            return List.of();
        }
        try {
            return JSON.readValue((String) rows.get(0).get("value"), new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("後端的 " + CONFIG_KEY + " 不是資訊頁的清單：" + rows.get(0).get("value"), e);
        }
    }

    void write(List<Map<String, String>> pages) {
        world.backend().setTenantConfig(world.defaults().tenantId(), CONFIG_KEY, pages);
    }

    Optional<Map<String, String>> titled(String title) {
        return read().stream().filter(page -> title.strip().equals(page.get("title"))).findFirst();
    }
}
