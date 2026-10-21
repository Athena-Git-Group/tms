package com.athena.tms.common.codes;

import java.util.List;

/** inspections.result（＝V1 CHECK chk_inspections_result）。 */
public final class InspectionResult {

    public static final String PASSED = "passed";
    public static final String FAILED = "failed";

    public static final List<String> ALL = List.of(PASSED, FAILED);

    private InspectionResult() {
    }
}
