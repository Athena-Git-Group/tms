package com.athena.tms.kb;

/**
 * 查一下的一則推薦問題（contracts 的 KbSuggestion）。scope 告訴員工這個問題
 * 會查到哪一類資料，例如「SOP · 您剛剛跳過這項」或「房間記錄」。
 */
public record KbSuggestion(String question, String scope) {
}
