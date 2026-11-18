package com.athena.tms.dto.desk;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 櫃檯看板的一次讀取。總覽看板、待派單頁、數量卡與徽章都從這一份算，
 * 畫面上的每個數字才對得起來，所以不另拆統計用的型別。
 */
public record DeskBoard(
        OffsetDateTime serverTime,
        LocalDate businessDate,
        List<Integer> floors,
        List<DeskTask> tasks,
        DeskMyToday myToday) {}
