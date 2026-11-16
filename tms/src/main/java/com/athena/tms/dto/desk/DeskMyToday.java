package com.athena.tms.dto.desk;

/** 登入者本人今天派出與當場回答的張數，待派單頁的空狀態用。 */
public record DeskMyToday(int assignedCount, int answeredCount) {}
