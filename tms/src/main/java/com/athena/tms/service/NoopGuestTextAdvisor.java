package com.athena.tms.service;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * MVP 不接 LLM，但呼叫端不該為「有沒有接」寫 if 分支——用一個永遠沒建議的
 * 實作佔住插拔點，之後接真 LLM 時替換這顆 bean，呼叫端一行不改。
 */
@Component
public class NoopGuestTextAdvisor implements GuestTextAdvisor {

    @Override
    public Optional<Advice> advise(long tenantId, String text, String lang) {
        return Optional.empty();
    }
}
