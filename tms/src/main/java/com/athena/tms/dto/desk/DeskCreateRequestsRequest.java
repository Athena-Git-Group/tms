package com.athena.tms.dto.desk;

import java.util.List;

public record DeskCreateRequestsRequest(String roomNo, List<DeskCreateRequestItem> items, DeskCreateRequestOther other) {}
