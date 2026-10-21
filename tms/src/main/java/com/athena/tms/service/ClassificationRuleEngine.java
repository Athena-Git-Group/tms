package com.athena.tms.service;

import com.athena.tms.common.codes.Dept;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.common.codes.TaskType;
import com.athena.tms.service.GuestTenantConfigReader.ClassificationRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 規則引擎 v1（A10）：租戶設定的關鍵字→部門映射。判準按「部門」數而不是
 * 「規則」數——兩條規則指向同一部門時去向沒有歧義，照樣直入 pool；
 * 真正的歧義是部門互相衝突（或完全沒線索），那才該停在 triage 讓櫃檯判讀，
 * 錯派部門的成本（跑錯人、再轉派）高於櫃檯多看一眼。
 */
@Component
public class ClassificationRuleEngine {

    /**
     * 自由文字的去向：routing 是建單參數的來源，ruleId 進事件留痕——
     * 「為什麼派給工務」要能在事後回答（gherkin：命中留痕可解釋）。
     */
    public record Routing(String initialState, String dept, String type, String ruleId) {
    }

    public Routing route(List<ClassificationRule> rules, String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        List<ClassificationRule> hits = new ArrayList<>();
        Set<String> depts = new LinkedHashSet<>();
        for (ClassificationRule rule : rules) {
            for (String keyword : rule.keywords()) {
                if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                    hits.add(rule);
                    depts.add(rule.dept());
                    break;
                }
            }
        }
        if (depts.size() == 1) {
            // 同部門多條規則命中時留第一條的 rule_id：設定順序即優先序，
            // 留痕要可重現，不能隨集合迭代順序漂移
            ClassificationRule first = hits.getFirst();
            return new Routing(TaskState.POOL, first.dept(),
                    first.type() != null ? first.type() : TaskType.OTHER, first.ruleId());
        }
        // 命中 0 或 >1 部門→triage 由櫃檯判讀（dept=frontdesk 是「待判讀佇列
        // 歸櫃檯看」的宣告，不是說這事最後由櫃檯做）
        return new Routing(TaskState.TRIAGE, Dept.FRONTDESK, TaskType.OTHER, null);
    }
}
