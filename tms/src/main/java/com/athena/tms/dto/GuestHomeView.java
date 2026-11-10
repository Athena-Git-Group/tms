package com.athena.tms.dto;

import com.athena.tms.dto.GuestMenuItemView;
import com.athena.tms.service.GuestTenantConfigReader;
import java.util.List;

public record GuestHomeView(List<GuestTenantConfigReader.InfoPage> infoPages, List<GuestMenuItemView> menu) {
}
