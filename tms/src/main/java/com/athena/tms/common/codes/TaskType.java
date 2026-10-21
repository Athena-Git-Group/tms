package com.athena.tms.common.codes;

import java.util.List;

/** tasks.type（＝V1 CHECK chk_tasks_type）。 */
public final class TaskType {

    public static final String AMENITY = "amenity";
    public static final String REPAIR = "repair";
    public static final String CLEANING = "cleaning";
    public static final String OTHER = "other";

    public static final List<String> ALL = List.of(AMENITY, REPAIR, CLEANING, OTHER);

    private TaskType() {
    }
}
