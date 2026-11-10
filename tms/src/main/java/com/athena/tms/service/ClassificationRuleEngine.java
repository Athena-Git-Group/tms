package com.athena.tms.service;

import com.athena.tms.service.GuestTenantConfigReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ClassificationRuleEngine {
    public Routing route(List<GuestTenantConfigReader.ClassificationRule> rules, String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        ArrayList hits = new ArrayList();
        LinkedHashSet<String> depts = new LinkedHashSet<String>();
        block0: for (GuestTenantConfigReader.ClassificationRule rule : rules) {
            for (String keyword : rule.keywords()) {
                if (!normalized.contains(keyword.toLowerCase(Locale.ROOT))) continue;
                hits.add(rule);
                depts.add(rule.dept());
                continue block0;
            }
        }
        if (depts.size() == 1) {
            GuestTenantConfigReader.ClassificationRule first = (GuestTenantConfigReader.ClassificationRule)hits.getFirst();
            return new Routing("pool", first.dept(), first.type() != null ? first.type() : "other", first.ruleId());
        }
        return new Routing("triage", "frontdesk", "other", null);
    }

    public record Routing(String initialState, String dept, String type, String ruleId) {
    }
}
