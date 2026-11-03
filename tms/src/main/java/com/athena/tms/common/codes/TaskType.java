/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.common.codes;

import java.util.List;

public final class TaskType {
    public static final String AMENITY = "amenity";
    public static final String REPAIR = "repair";
    public static final String CLEANING = "cleaning";
    public static final String OTHER = "other";
    public static final List<String> ALL = List.of("amenity", "repair", "cleaning", "other");

    private TaskType() {
    }
}
