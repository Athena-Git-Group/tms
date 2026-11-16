package com.athena.tms.dto.desk;

/**
 * 櫃檯派單的請求。{@code via} 是按鈕（button）或拖曳（drag），只影響歷程文字；
 * 沒帶時為 null，由派單的規則視為 button。
 */
public record DeskAssignRequest(String dept, String via) {}
