/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Component
 */
package com.athena.tms.service;

import com.athena.tms.service.GuestTextAdvisor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NoopGuestTextAdvisor
implements GuestTextAdvisor {
    @Override
    public Optional<GuestTextAdvisor.Advice> advise(long tenantId, String text, String lang) {
        return Optional.empty();
    }
}
