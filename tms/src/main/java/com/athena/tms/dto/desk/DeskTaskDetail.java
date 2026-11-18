package com.athena.tms.dto.desk;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 櫃檯視角的單筆詳情：卡片上已有的資料，加上詳情面板與當場回答面板才需要的內容與歷程。
 *
 * <p>契約把它定義成「{@link DeskTask} 的全部欄位再加幾項」，JSON 是同一層。
 * 這裡用 {@code @JsonUnwrapped} 攤平，卡片欄位只維護 {@link DeskTask} 一份，
 * 兩邊不會各改各的而對不上。
 */
public record DeskTaskDetail(
        @JsonUnwrapped DeskTask task,
        OffsetDateTime serverTime,
        String answer,
        String answeredByName,
        Guest guest,
        String createdByName,
        Accepted accepted,
        Cancellation cancellation,
        Repair repair,
        List<DeskTimelineEntry> timeline) {

    /** 這張單的住客；訂房資料沒有人數，所以不帶人數。 */
    public record Guest(String name, LocalDate checkinDate, LocalDate checkoutDate) {}

    /** 這張單怎麼到目前負責人手上。 */
    public record Accepted(String via, OffsetDateTime at, String byName, String previousAssigneeName) {}

    /** 取消的說明；取消的時間看 {@link DeskTask#endedAt()}。 */
    public record Cancellation(String reason, String byName) {}

    /** 工務單才有的完修結果與驗收主管。 */
    public record Repair(String resolutionType, String verifiedByName) {}
}
