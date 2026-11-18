package com.athena.tms.dto.desk;

/** 當場回答面板的一則常用回覆，內容就是住客資訊頁的一頁。 */
public record DeskQuickReply(String key, String title, String body) {}
