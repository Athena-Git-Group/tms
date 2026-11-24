package com.athena.tms.dto.desk;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

public enum DeskMenuKey {
    TOWEL("毛巾", "housekeeping", "amenity", 9, null),
    BATH_TOWEL("浴巾", "housekeeping", "amenity", 9, null),
    WATER("礦泉水", "housekeeping", "amenity", 9, null),
    SLIPPERS("拖鞋", "housekeeping", "amenity", 9, null),
    TOOTHBRUSH_KIT("牙刷組", "housekeeping", "amenity", 9, null),
    BABY_COT("嬰兒床", "housekeeping", "amenity", 2, null),
    EXTRA_BED("加床", "housekeeping", "amenity", 2, null),
    ICE("冰塊", "housekeeping", "amenity", 2, null),
    AIR_CONDITIONING("冷氣", "engineering", "repair", 1, "air_conditioning"),
    HOT_WATER("熱水", "engineering", "repair", 1, "plumbing_electrical"),
    LIGHTING("燈具", "engineering", "repair", 1, "plumbing_electrical"),
    TELEVISION("電視", "engineering", "repair", 1, "furniture_equipment");

    private final String key;
    private final String dept;
    private final String type;
    private final int maxQuantity;
    private final String repairCategory;

    DeskMenuKey(String key, String dept, String type, int maxQuantity, String repairCategory) {
        this.key = key;
        this.dept = dept;
        this.type = type;
        this.maxQuantity = maxQuantity;
        this.repairCategory = repairCategory;
    }

    @JsonValue
    public String key() {
        return key;
    }

    public String dept() {
        return dept;
    }

    public String type() {
        return type;
    }

    public int maxQuantity() {
        return maxQuantity;
    }

    public String repairCategory() {
        return repairCategory;
    }

    public boolean engineering() {
        return "engineering".equals(dept);
    }

    public int recordedQuantity(int requested) {
        return engineering() ? 1 : requested;
    }

    public String title(int quantity) {
        if (engineering()) {
            return key + "有問題";
        }
        if (this == ICE) {
            return key + " ×" + quantity + " 桶";
        }
        return key + " ×" + quantity;
    }

    public static Optional<DeskMenuKey> fromKey(String key) {
        return Arrays.stream(values()).filter(candidate -> candidate.key.equals(key)).findFirst();
    }
}
