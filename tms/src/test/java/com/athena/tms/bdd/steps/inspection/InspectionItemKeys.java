package com.athena.tms.bdd.steps.inspection;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

final class InspectionItemKeys {

    private InspectionItemKeys() {
    }

    static String fromLabel(String label) {
        return "insp_" + HexFormat.of().formatHex(label.getBytes(StandardCharsets.UTF_8));
    }
}
