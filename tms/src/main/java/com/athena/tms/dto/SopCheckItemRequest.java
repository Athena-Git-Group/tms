package com.athena.tms.dto;

import java.util.UUID;

public record SopCheckItemRequest(UUID sopItemId, String result) {}
