package com.athena.tms.dto.desk;

/** 住客打字的需求原文與單上既有的翻譯。後端不產生翻譯，{@code translated} 沒有就是 null。 */
public record DeskGuestText(String original, String lang, String translated) {}
