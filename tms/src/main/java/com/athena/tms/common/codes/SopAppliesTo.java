package com.athena.tms.common.codes;

import java.util.List;

/** sop_items.applies_to（＝V1 CHECK chk_sop_items_applies_to；SOP 與查房項目同構共表）。 */
public final class SopAppliesTo {

    public static final String CLEANING = "cleaning";
    public static final String INSPECTION = "inspection";

    public static final List<String> ALL = List.of(CLEANING, INSPECTION);

    private SopAppliesTo() {
    }
}
