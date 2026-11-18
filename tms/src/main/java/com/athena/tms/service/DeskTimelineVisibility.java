package com.athena.tms.service;

import java.util.ArrayList;
import java.util.List;

final class DeskTimelineVisibility {

    private DeskTimelineVisibility() {
    }

    static List<Integer> visibleIndexes(List<String> types) {
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            String type = types.get(i);
            if ("triaged".equals(type) && i + 1 < types.size() && "triage_undone".equals(types.get(i + 1))) {
                i++;
                continue;
            }
            if ("sop_checked".equals(type)) {
                continue;
            }
            visible.add(i);
        }
        return visible;
    }
}
