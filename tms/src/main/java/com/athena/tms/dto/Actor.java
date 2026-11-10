package com.athena.tms.dto;

import java.util.UUID;

public record Actor(String role, UUID id) {
    public static Actor guest() {
        return new Actor("guest", null);
    }

    public static Actor system() {
        return new Actor("system", null);
    }

    public static Actor staff(String role, UUID staffId) {
        return new Actor(role, staffId);
    }
}
