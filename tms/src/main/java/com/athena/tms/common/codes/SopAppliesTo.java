/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.common.codes;

import java.util.List;

public final class SopAppliesTo {
    public static final String CLEANING = "cleaning";
    public static final String INSPECTION = "inspection";
    public static final List<String> ALL = List.of("cleaning", "inspection");

    private SopAppliesTo() {
    }
}
