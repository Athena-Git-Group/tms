package com.athena.tms.dto.desk;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DeskBoard(
        OffsetDateTime serverTime,
        LocalDate businessDate,
        List<Integer> floors,
        List<DeskTask> tasks,
        DeskMyToday myToday) {}
