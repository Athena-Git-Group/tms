package com.athena.tms.bdd.steps.inspection;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 查房項目顯示名稱（label）→ {@code sop_items.item_key} 的唯一一條產生規則。
 * 查房/dsl.md 說 item key「由 label 產生」但沒有指定格式；Given 建項目與 When 解析
 * 表格都得用同一條規則，所以集中在這裡，不讓兩邊各自推導後對不上。
 * 用 UTF-8 位元組的十六進位而非 hashCode，避免碰撞也不依賴 JVM 實作。
 */
final class InspectionItemKeys {

    private InspectionItemKeys() {
    }

    static String fromLabel(String label) {
        return "insp_" + HexFormat.of().formatHex(label.getBytes(StandardCharsets.UTF_8));
    }
}
