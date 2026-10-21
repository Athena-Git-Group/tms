package com.athena.tms.service;

import com.athena.tms.dao.TenantConfigRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 住客流程用到的四把 tenant_config 鍵的讀取面：資訊頁、選單、
 * 分類規則、掃碼限流門檻皆租戶可調，不寫死。每次呼叫重讀 DB，
 * 改設定立即生效比省一次索引查詢重要；四把鍵都沿用同一原則，
 * 不做程序內快取（快取失效策略又是一個要維護的東西）。
 *
 * <p>value 是 jsonb 原文，這裡用 readTree 手動取欄而不是繫結註解：設定是
 * 營運人員手上的資料，缺欄、多欄都不該讓整個請求 500——缺什麼就給空值，
 * 壞列直接跳過。
 */
@Component
public class GuestTenantConfigReader {

    static final String KEY_GUEST_INFO_PAGES = "guest_info_pages";
    static final String KEY_REQUEST_MENU = "request_menu";
    static final String KEY_CLASSIFICATION_RULES = "classification_rules";
    static final String KEY_SCAN_FAIL_THRESHOLD = "scan_fail_threshold";

    /** A7 的預設值：租戶沒種 scan_fail_threshold 鍵時限流不能形同虛設。 */
    static final int DEFAULT_SCAN_FAIL_THRESHOLD = 10;

    public record InfoPage(String key, String title, String body) {
    }

    public record MenuItem(String key, String label, String dept, String type) {
    }

    public record ClassificationRule(String ruleId, List<String> keywords, String dept, String type) {
    }

    private final TenantConfigRepository tenantConfigRepository;
    private final ObjectMapper objectMapper;

    public GuestTenantConfigReader(TenantConfigRepository tenantConfigRepository, ObjectMapper objectMapper) {
        this.tenantConfigRepository = tenantConfigRepository;
        this.objectMapper = objectMapper;
    }

    public List<InfoPage> infoPages(long tenantId) {
        List<InfoPage> pages = new ArrayList<>();
        for (JsonNode node : readArray(tenantId, KEY_GUEST_INFO_PAGES)) {
            pages.add(new InfoPage(text(node, "key"), text(node, "title"), text(node, "body")));
        }
        return pages;
    }

    public List<MenuItem> requestMenu(long tenantId) {
        List<MenuItem> items = new ArrayList<>();
        for (JsonNode node : readArray(tenantId, KEY_REQUEST_MENU)) {
            MenuItem item = new MenuItem(
                    text(node, "key"), text(node, "label"), text(node, "dept"), text(node, "type"));
            // 缺 key 選不到、缺 dept/type 建不了單——殘缺項直接不上架，
            // 好過住客點了才 500
            if (item.key() != null && item.dept() != null && item.type() != null) {
                items.add(item);
            }
        }
        return items;
    }

    public List<ClassificationRule> classificationRules(long tenantId) {
        List<ClassificationRule> rules = new ArrayList<>();
        for (JsonNode node : readArray(tenantId, KEY_CLASSIFICATION_RULES)) {
            List<String> keywords = new ArrayList<>();
            JsonNode keywordsNode = node.get("keywords");
            if (keywordsNode != null && keywordsNode.isArray()) {
                keywordsNode.forEach(keyword -> {
                    if (keyword.isTextual() && !keyword.asText().isBlank()) {
                        keywords.add(keyword.asText());
                    }
                });
            }
            ClassificationRule rule = new ClassificationRule(
                    text(node, "rule_id"), keywords, text(node, "dept"), text(node, "type"));
            // 沒關鍵字的規則永遠不命中、沒部門的規則命中了也無處可派——都當不存在
            if (!rule.keywords().isEmpty() && rule.dept() != null) {
                rules.add(rule);
            }
        }
        return rules;
    }

    public int scanFailThreshold(long tenantId) {
        return tenantConfigRepository.findByTenantIdAndKey(tenantId, KEY_SCAN_FAIL_THRESHOLD)
                .map(config -> {
                    JsonNode node = objectMapper.readTree(config.getValue());
                    return node.isNumber() ? node.asInt() : DEFAULT_SCAN_FAIL_THRESHOLD;
                })
                .orElse(DEFAULT_SCAN_FAIL_THRESHOLD);
    }

    private JsonNode readArray(long tenantId, String key) {
        return tenantConfigRepository.findByTenantIdAndKey(tenantId, key)
                .map(config -> {
                    JsonNode node = objectMapper.readTree(config.getValue());
                    return node.isArray() ? node : objectMapper.createArrayNode();
                })
                .orElseGet(objectMapper::createArrayNode);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }
}
