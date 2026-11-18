package com.athena.tms.controller;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.HotelCode;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.dao.TenantRepository;
import com.athena.tms.dto.StaffChangePasswordRequest;
import com.athena.tms.dto.StaffLoginRequest;
import com.athena.tms.dto.StaffLoginResponse;
import com.athena.tms.dto.desk.PublicHotel;
import com.athena.tms.service.StaffAccountLoginService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StaffAccountController {

    private final StaffAccountRepository staffAccountRepository;
    private final StaffAccountLoginService staffAccountLoginService;
    private final TenantRepository tenantRepository;

    public StaffAccountController(
            StaffAccountRepository staffAccountRepository,
            StaffAccountLoginService staffAccountLoginService,
            TenantRepository tenantRepository) {
        this.staffAccountRepository = staffAccountRepository;
        this.staffAccountLoginService = staffAccountLoginService;
        this.tenantRepository = tenantRepository;
    }

    public record StaffAccountView(UUID id, String name, String role, String dept, boolean active) {}

    @GetMapping("/staff-accounts")
    public List<StaffAccountView> list() {
        long tenantId = TenantContext.require();
        return staffAccountRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .map(sa -> new StaffAccountView(sa.getId(), sa.getName(), sa.getRole(), sa.getDept(), sa.isActive()))
                .toList();
    }

    /**
     * 登入前用飯店識別代碼查飯店名稱，不用登入就能呼叫，回應只有名稱一項。
     *
     * <p>代碼原樣拿去比對，不修剪也不轉小寫。對不到飯店時不分原因，一律回同一個 404：
     * 不存在、大小寫不符、格式不對、拿內部流水號來查，回應都一模一樣。
     * 要是不同原因回不同內容，呼叫的人就能拿流水號一個個試，從回應的差別推出有哪些飯店。
     *
     * <p>這支端點不用登入，誰都能拿任意字串來試。格式不合代碼規則的字串在資料庫裡不可能有對應的飯店，
     * 不必為它們查資料庫；它們和其他查不到的情況回同一個 404，回應看不出是格式不對。
     *
     * <p>飯店主檔不在列層級權限的範圍內，應用的資料庫帳號本來就能讀，所以這裡沒有登入的飯店身分也查得到。
     */
    @GetMapping("/public/hotels/{hotelCode}")
    public PublicHotel readPublicHotel(@PathVariable String hotelCode) {
        return Optional.of(hotelCode)
                .filter(HotelCode::isWellFormed)
                .flatMap(tenantRepository::findByCode)
                .map(tenant -> new PublicHotel(tenant.getName()))
                .orElseThrow(() -> new NotFoundException("查無此飯店"));
    }

    @PostMapping("/staff-accounts/login")
    public StaffLoginResponse login(@RequestBody StaffLoginRequest request) {
        return staffAccountLoginService.login(request);
    }

    @PostMapping("/staff-accounts/change-password")
    public StaffLoginResponse changePassword(@RequestBody StaffChangePasswordRequest request) {
        CurrentActor actor = CurrentActor.require();
        return staffAccountLoginService.changePassword(TenantContext.require(), actor.staffId(), request);
    }
}
