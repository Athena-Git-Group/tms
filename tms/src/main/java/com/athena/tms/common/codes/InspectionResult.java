/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.common.codes;

import java.util.List;

public final class InspectionResult {
    public static final String PASSED = "passed";
    public static final String FAILED = "failed";
    public static final List<String> ALL = List.of("passed", "failed");

    private InspectionResult() {
    }
}
