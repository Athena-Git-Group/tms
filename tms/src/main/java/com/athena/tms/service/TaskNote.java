package com.athena.tms.service;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

final class TaskNote {
    private final Map<String, Object> fields = new LinkedHashMap<>();

    private TaskNote() {
    }

    static TaskNote menuItem(String menuKey, int quantity) {
        return new TaskNote().menuKey(menuKey).with("quantity", quantity);
    }

    static TaskNote guestText(String text) {
        return new TaskNote().with("text", text);
    }

    TaskNote menuKey(String menuKey) {
        return this.with("menu_key", menuKey);
    }

    TaskNote with(String field, Object value) {
        if (value != null) {
            this.fields.put(field, value);
        }
        return this;
    }

    String toJson(ObjectMapper objectMapper) {
        return objectMapper.writeValueAsString(this.fields);
    }
}
