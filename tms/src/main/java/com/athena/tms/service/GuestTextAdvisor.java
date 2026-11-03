/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.service;

import java.util.Optional;

public interface GuestTextAdvisor {
    public Optional<Advice> advise(long var1, String var3, String var4);

    public record Advice(String translatedText, String suggestedDept) {
    }
}
