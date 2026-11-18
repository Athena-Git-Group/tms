package com.athena.tms.bdd.steps.account;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import io.cucumber.java.zh_tw.當;
import java.util.Map;

public class AccountWhenSteps {

    private static final String FRONTDESK_WEB_CLIENT = "frontdesk_web";

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;

    public AccountWhenSteps(ScenarioWorld world, BddApiClient apiClient, DbProbe dbProbe) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
    }

    @當("{string} 用員工編號 {string} 密碼 {string} 登入")
    public void login(String staffName, String loginId, String password) {
        Map<String, Object> body = Map.of("loginId", loginId, "password", password);
        apiClient.post("/staff-accounts/login", BddApiClient.Auth.none(), body);
    }

    @當("{string} 帶飯店代碼 {string} 用員工編號 {string} 密碼 {string} 登入")
    public void loginAtHotel(String staffName, String hotelCode, String loginId, String password) {
        Map<String, Object> body = Map.of("loginId", loginId, "password", password, "hotelCode", hotelCode);
        apiClient.post("/staff-accounts/login", BddApiClient.Auth.none(), body);
    }

    @當("{string} 從櫃檯畫面用員工編號 {string} 密碼 {string} 登入")
    public void loginFromFrontdeskWeb(String staffName, String loginId, String password) {
        Map<String, Object> body = Map.of("loginId", loginId, "password", password, "client", FRONTDESK_WEB_CLIENT);
        apiClient.post("/staff-accounts/login", BddApiClient.Auth.none(), body);
    }

    @當("{string} 把新密碼設為 {string}")
    public void changeOwnPassword(String staffName, String newPassword) {
        Map<String, Object> body = Map.of("newPassword", newPassword);
        apiClient.post("/staff-accounts/change-password", BddApiClient.Auth.staff(world.jwt(staffName)), body);
    }

    @當("沒有登入的人用識別代碼 {string} 查飯店名稱")
    public void readPublicHotelByCode(String hotelCode) {
        apiClient.get("/public/hotels/" + hotelCode, BddApiClient.Auth.none());
    }

    @當("沒有登入的人用 {string} 這家飯店的內部編號查飯店名稱")
    public void readPublicHotelByInternalId(String hotelCode) {
        Object tenantId = dbProbe
                .queryOne("SELECT id FROM tenants WHERE code = ?", hotelCode)
                .orElseThrow(() -> new IllegalStateException("找不到識別代碼是「" + hotelCode + "」的飯店，請先在 Given 建立"))
                .get("id");
        apiClient.get("/public/hotels/" + tenantId, BddApiClient.Auth.none());
    }
}
