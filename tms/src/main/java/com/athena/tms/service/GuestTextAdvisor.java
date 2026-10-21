package com.athena.tms.service;

import java.util.Optional;

/**
 * LLM 輔助的插拔點，LLM 僅做翻譯與部門建議。介面先立、實作後補的原因：
 * 歸屬判定的真相必須留在規則引擎——LLM 的輸出不可重現、不可解釋，讓它直接
 * 改歸屬等於把「為什麼派給工務」變成沒人答得出的問題。所以合約上建議就只是
 * 建議：呼叫端留稽核（記進任務 note 與日誌），路由決策照舊走規則。
 */
public interface GuestTextAdvisor {

    /**
     * @return empty＝無建議（預設 no-op 實作永遠如此）；有值時呼叫端只記錄、
     *         不據此改變 state/dept
     */
    Optional<Advice> advise(long tenantId, String text, String lang);

    record Advice(String translatedText, String suggestedDept) {
    }
}
