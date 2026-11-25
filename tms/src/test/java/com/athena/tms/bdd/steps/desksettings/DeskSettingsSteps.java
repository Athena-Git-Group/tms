package com.athena.tms.bdd.steps.desksettings;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.那麼;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

public class DeskSettingsSteps {

    private static final String PATH = "/desk/settings";

    private enum Setting {
        INSPECTION_REQUIRED("需要房務主管查房", "inspectionRequired", "inspection_waived_enabled", true),
        REPAIR_VERIFICATION_REQUIRED("需要工務主管驗收", "repairVerificationRequired", "repair_verification_required", false);

        final String label;
        final String field;
        final String configKey;
        final boolean storedInverted;

        Setting(String label, String field, String configKey, boolean storedInverted) {
            this.label = label;
            this.field = field;
            this.configKey = configKey;
            this.storedInverted = storedInverted;
        }

        static Setting of(String label) {
            for (Setting setting : values()) {
                if (setting.label.equals(label)) {
                    return setting;
                }
            }
            throw new IllegalArgumentException("不認得的設定: " + label);
        }

        boolean switchOnFromStored(String stored) {
            return storedInverted ? !"true".equals(stored) : !"false".equals(stored);
        }
    }

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final TenantFixtures tenantFixtures;
    private Map<String, Map<String, Object>> configBefore;

    public DeskSettingsSteps(ScenarioWorld world, BddApiClient apiClient, DbProbe dbProbe, TenantFixtures tenantFixtures) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.tenantFixtures = tenantFixtures;
    }

    @假設("{string} 已在櫃檯設定把「{word}」設為 {string}")
    public void alreadySetSwitch(String staffName, String label, String onOff) {
        ScenarioWorld.ApiResponse resp = patch(staffName, Map.of(Setting.of(label).field, parseSwitch(onOff)));
        if (resp.status() != 200) {
            throw new IllegalStateException("前提「" + staffName + " 把" + label + "設為" + onOff + "」沒鋪起來：status="
                    + resp.status() + "，body=" + resp.body());
        }
    }

    @當("{string} 讀取查房與驗收設定")
    public void readSettings(String staffName) {
        apiClient.get(PATH, BddApiClient.Auth.staff(world.jwt(staffName)));
    }

    @當("{string} 在櫃檯設定把「{word}」設為 {string}")
    public void setSwitch(String staffName, String label, String onOff) {
        configBefore = storedConfig();
        patch(staffName, Map.of(Setting.of(label).field, parseSwitch(onOff)));
    }

    @當("{string} 送出沒有任何開關的查房與驗收設定")
    public void sendEmptySettings(String staffName) {
        configBefore = storedConfig();
        patch(staffName, Map.of());
    }

    @當("{string} 把查房與驗收設定的「{word}」送成 {string}")
    public void sendNonBooleanValue(String staffName, String label, String raw) {
        configBefore = storedConfig();
        patch(staffName, Map.of(Setting.of(label).field, raw));
    }

    @那麼("查房與驗收設定為：")
    public void settingsAre(DataTable table) {
        Map<Setting, Boolean> expected = new LinkedHashMap<>();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            expected.put(Setting.of(row.get("設定")), parseSwitch(row.get("開關")));
        }

        ScenarioWorld.ApiResponse resp = world.lastResponse();
        assertBody("上一次回應", resp, expected);

        String managerName = dbProbe
                .queryOne("SELECT name FROM staff_accounts WHERE tenant_id = ? AND role = 'manager' AND active LIMIT 1",
                        tenantFixtures.testTenantId())
                .map(row -> String.valueOf(row.get("name")))
                .orElseThrow(() -> new IllegalStateException("本場景沒有主管，無法再讀一次設定"));
        assertBody("主管再讀一次", apiClient.get(PATH, BddApiClient.Auth.staff(world.jwt(managerName))), expected);

        Map<String, Map<String, Object>> stored = storedConfig();
        expected.forEach((setting, on) -> {
            Map<String, Object> row = stored.get(setting.configKey);
            String value = row == null ? null : (String) row.get("value");
            if (setting.switchOnFromStored(value) != on) {
                throw new AssertionError("tenant_config." + setting.configKey + " 換算後預期 " + (on ? "開" : "關")
                        + "，實際存的是 " + value);
            }
        });
    }

    @那麼("「{word}」最後由 {string} 修改")
    public void lastUpdatedBy(String label, String staffName) {
        Map<String, Object> row = storedConfig().get(Setting.of(label).configKey);
        if (row == null) {
            throw new AssertionError("tenant_config 沒有 " + Setting.of(label).configKey);
        }
        String expected = world.staffId(staffName);
        if (!expected.equalsIgnoreCase(String.valueOf(row.get("updated_by")))) {
            throw new AssertionError("tenant_config." + Setting.of(label).configKey + ".updated_by 預期 " + staffName + "（"
                    + expected + "），實際 " + row.get("updated_by"));
        }
    }

    @那麼("這次設定操作被拒絕，錯誤碼為 {string}")
    public void settingsRejected(String code) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        int expectedStatus = switch (code) {
            case "forbidden" -> 403;
            case "validation_failed" -> 422;
            default -> throw new IllegalArgumentException("不支援的錯誤碼: " + code);
        };
        JsonNode json = resp.json();
        String actualCode = json == null ? null : json.path("code").asText(null);
        boolean hasMessage = json != null && json.hasNonNull("message");
        if (resp.status() != expectedStatus || !code.equals(actualCode) || !hasMessage) {
            throw new AssertionError("預期 " + expectedStatus + " 且 code=" + code + "（帶 message），實際 status="
                    + resp.status() + "，body=" + resp.body());
        }
    }

    @那麼("查房與驗收設定沒有被改動")
    public void settingsUnchanged() {
        if (configBefore == null) {
            throw new IllegalStateException("前一個 When 沒有記下設定的原值");
        }
        Map<String, Map<String, Object>> after = storedConfig();
        if (!Objects.equals(configBefore, after)) {
            throw new AssertionError("tenant_config 被改動了：操作前 " + configBefore + "，操作後 " + after);
        }
    }

    private ScenarioWorld.ApiResponse patch(String staffName, Map<String, Object> body) {
        return apiClient.patch(PATH, BddApiClient.Auth.staff(world.jwt(staffName)), body);
    }

    private static void assertBody(String what, ScenarioWorld.ApiResponse resp, Map<Setting, Boolean> expected) {
        JsonNode json = resp.json();
        if (resp.status() != 200 || json == null) {
            throw new AssertionError(what + "預期 200，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        expected.forEach((setting, on) -> {
            JsonNode value = json.path(setting.field);
            if (!value.isBoolean() || value.asBoolean() != on) {
                throw new AssertionError(what + "的 " + setting.field + " 預期 " + on + "，實際 body=" + resp.body());
            }
        });
    }

    private Map<String, Map<String, Object>> storedConfig() {
        List<Map<String, Object>> rows = dbProbe.queryMaps(
                """
                SELECT key, value #>> '{}' AS value, updated_by
                  FROM tenant_config
                 WHERE tenant_id = ? AND key IN ('inspection_waived_enabled', 'repair_verification_required')
                """,
                tenantFixtures.testTenantId());
        Map<String, Map<String, Object>> byKey = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new HashMap<>();
            copy.put("value", row.get("value"));
            copy.put("updated_by", row.get("updated_by") == null ? null : String.valueOf(row.get("updated_by")));
            byKey.put(String.valueOf(row.get("key")), copy);
        }
        return byKey;
    }

    private static boolean parseSwitch(String onOff) {
        return switch (onOff) {
            case "開" -> true;
            case "關" -> false;
            default -> throw new IllegalArgumentException("開關只能是 開 或 關，收到: " + onOff);
        };
    }
}
