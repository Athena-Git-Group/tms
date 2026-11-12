package com.athena.tms.dto;

import com.athena.tms.common.codes.TaskState;
import com.athena.tms.common.error.BadRequestException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 櫃檯任務清單的查詢條件，在 controller 收到請求時就解析完成，之後的查詢只依這個型別組條件。
 *
 * <p>日期範圍一定有上下界而且不超過 {@link #MAX_RANGE_DAYS} 天：清單不分頁，
 * 一家飯店一天約一百五十張單，沒有界線的話一年後每次打開清單都要回傳數萬筆。
 * 兩端都沒帶就是今天；只帶一端時另一端取同一天，也就是只查那一天。
 *
 * <p>{@code states} 為空集合代表不限狀態。
 */
public record DeskTaskFilter(Optional<UUID> assigneeId, Set<String> states, LocalDate from, LocalDate to) {
    public static final int MAX_RANGE_DAYS = 31;

    public static DeskTaskFilter parse(UUID assigneeId, List<String> states, LocalDate from, LocalDate to, LocalDate today) {
        LocalDate start = from != null ? from : (to != null ? to : today);
        LocalDate end = to != null ? to : start;
        if (start.isAfter(end)) {
            throw new BadRequestException("from 不可晚於 to");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BadRequestException("from 與 to 最多相差 " + MAX_RANGE_DAYS + " 天");
        }
        Set<String> wanted = states == null ? Set.of() : Set.copyOf(states);
        Set<String> unknown = wanted.stream().filter(state -> !TaskState.ALL.contains(state)).collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            throw new BadRequestException("未知的任務狀態: " + String.join(", ", unknown));
        }
        return new DeskTaskFilter(Optional.ofNullable(assigneeId), wanted, start, end);
    }

    public boolean covers(LocalDate day) {
        return !day.isBefore(this.from) && !day.isAfter(this.to);
    }
}
