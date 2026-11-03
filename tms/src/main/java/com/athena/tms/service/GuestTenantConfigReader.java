package com.athena.tms.service;

import com.athena.tms.dao.TenantConfigRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GuestTenantConfigReader {
    static final String KEY_GUEST_INFO_PAGES = "guest_info_pages";
    static final String KEY_REQUEST_MENU = "request_menu";
    static final String KEY_CLASSIFICATION_RULES = "classification_rules";
    static final String KEY_SCAN_FAIL_THRESHOLD = "scan_fail_threshold";
    static final int DEFAULT_SCAN_FAIL_THRESHOLD = 10;
    private final TenantConfigRepository tenantConfigRepository;
    private final ObjectMapper objectMapper;

    public GuestTenantConfigReader(TenantConfigRepository tenantConfigRepository, ObjectMapper objectMapper) {
        this.tenantConfigRepository = tenantConfigRepository;
        this.objectMapper = objectMapper;
    }

    public List<InfoPage> infoPages(long tenantId) {
        ArrayList<InfoPage> pages = new ArrayList<InfoPage>();
        for (JsonNode node : this.readArray(tenantId, KEY_GUEST_INFO_PAGES)) {
            pages.add(new InfoPage(GuestTenantConfigReader.text(node, "key"), GuestTenantConfigReader.text(node, "title"), GuestTenantConfigReader.text(node, "body")));
        }
        return pages;
    }

    public List<MenuItem> requestMenu(long tenantId) {
        ArrayList<MenuItem> items = new ArrayList<MenuItem>();
        for (JsonNode node : this.readArray(tenantId, KEY_REQUEST_MENU)) {
            MenuItem item = new MenuItem(GuestTenantConfigReader.text(node, "key"), GuestTenantConfigReader.text(node, "label"), GuestTenantConfigReader.text(node, "dept"), GuestTenantConfigReader.text(node, "type"));
            if (item.key() == null || item.dept() == null || item.type() == null) continue;
            items.add(item);
        }
        if (items.isEmpty()) {
            // 租戶還沒設定勾選單時用出廠內建品項（比照自動化規則的出廠內建）：
            // 常見備品預先對應到房務部，租戶一旦存了自己的 request_menu 就以租戶的為準。
            return FACTORY_REQUEST_MENU;
        }
        return items;
    }

    /** 出廠內建勾選單：key＝顯示名稱，全部對應房務備品。 */
    private static final List<MenuItem> FACTORY_REQUEST_MENU = List.of(
            new MenuItem("衣架", "衣架", "housekeeping", "amenity"),
            new MenuItem("毛巾", "毛巾", "housekeeping", "amenity"),
            new MenuItem("牙刷組", "牙刷組", "housekeeping", "amenity"),
            new MenuItem("礦泉水", "礦泉水", "housekeeping", "amenity"),
            new MenuItem("吹風機", "吹風機", "housekeeping", "amenity"));

    public List<ClassificationRule> classificationRules(long tenantId) {
        ArrayList<ClassificationRule> rules = new ArrayList<ClassificationRule>();
        for (JsonNode node : this.readArray(tenantId, KEY_CLASSIFICATION_RULES)) {
            ClassificationRule rule;
            ArrayList<String> keywords = new ArrayList<String>();
            JsonNode keywordsNode = node.get("keywords");
            if (keywordsNode != null && keywordsNode.isArray()) {
                keywordsNode.forEach(keyword -> {
                    if (keyword.isTextual() && !keyword.asText().isBlank()) {
                        keywords.add(keyword.asText());
                    }
                });
            }
            if ((rule = new ClassificationRule(GuestTenantConfigReader.text(node, "rule_id"), keywords, GuestTenantConfigReader.text(node, "dept"), GuestTenantConfigReader.text(node, "type"))).keywords().isEmpty() || rule.dept() == null) continue;
            rules.add(rule);
        }
        return rules;
    }

    public int scanFailThreshold(long tenantId) {
        return this.tenantConfigRepository.findByTenantIdAndKey(tenantId, KEY_SCAN_FAIL_THRESHOLD).map(config -> {
            JsonNode node = this.objectMapper.readTree(config.getValue());
            return node.isNumber() ? node.asInt() : 10;
        }).orElse(10);
    }

    private JsonNode readArray(long tenantId, String key) {
        return this.tenantConfigRepository.findByTenantIdAndKey(tenantId, key).map(config -> {
            JsonNode node = this.objectMapper.readTree(config.getValue());
            return node.isArray() ? node : this.objectMapper.createArrayNode();
        }).orElseGet(() -> ((ObjectMapper)this.objectMapper).createArrayNode());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    public record InfoPage(String key, String title, String body) {
    }

    public record MenuItem(String key, String label, String dept, String type) {
    }

    public record ClassificationRule(String ruleId, List<String> keywords, String dept, String type) {
    }
}
