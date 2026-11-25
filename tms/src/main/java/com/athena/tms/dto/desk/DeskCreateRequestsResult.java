package com.athena.tms.dto.desk;

import java.time.OffsetDateTime;
import java.util.List;

public record DeskCreateRequestsResult(OffsetDateTime serverTime, List<DeskTask> tasks) {}
