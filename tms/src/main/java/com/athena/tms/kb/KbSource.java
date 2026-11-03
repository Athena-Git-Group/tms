package com.athena.tms.kb;

/**
 * 查一下回答的一筆出處（contracts 的 KbSource）。excerpt 是被引用資料的原文，
 * 不含住客身分；updatedOn 用 ISO 日期字串（yyyy-MM-dd）。
 */
public record KbSource(String kind, String title, String excerpt, String updatedOn) {
}
