/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.dto;

import java.util.List;

public record InspectionRequest(List<Item> items) {

    public record Item(String key, Boolean pass) {
    }
}
