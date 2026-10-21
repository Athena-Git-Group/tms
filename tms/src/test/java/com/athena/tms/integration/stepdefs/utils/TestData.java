package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.common.codes.ActorRole;
import com.athena.tms.dto.Actor;

import java.util.UUID;

/**
 * 種子 migration（V2）寫進資料庫的固定識別碼與帳密，測試程式用這些常數引用種子資料。
 *
 * <p>UUID 刻意取可讀的固定值，讓測試失敗訊息一眼認得出是哪筆資料：
 * 房間的尾碼就是房號，員工的尾碼從 a001 起，訂單的尾碼從 b001 起。
 */
public final class TestData {

    public static final long TENANT_1 = 1L;
    public static final long TENANT_2 = 2L;

    public static final UUID ROOM_301 = UUID.fromString("00000000-0000-7000-8000-000000000301");
    public static final UUID ROOM_305 = UUID.fromString("00000000-0000-7000-8000-000000000305");
    public static final UUID ROOM_502 = UUID.fromString("00000000-0000-7000-8000-000000000502");

    public static final UUID STAFF_HK1 = UUID.fromString("00000000-0000-7000-8000-00000000a001");
    public static final UUID STAFF_MG1 = UUID.fromString("00000000-0000-7000-8000-00000000a003");
    public static final UUID STAFF_FD1 = UUID.fromString("00000000-0000-7000-8000-00000000a004");

    public static final UUID RESERVATION_B001 = UUID.fromString("00000000-0000-7000-8000-00000000b001");
    /** 房 301 的種子訂單。直呼 TaskEngine 建測試資料的任務預設綁定這一筆。 */
    public static final UUID RESERVATION_B002 = UUID.fromString("00000000-0000-7000-8000-00000000b002");

    public static final String PASSWORD = "demo1234";
    /**
     * 與種子資料相同的 bcrypt 雜湊，明文是 demo1234。
     * feature 表格自行建立的帳號要塞這把雜湊，才能通過真實登入流程取得 JWT。
     */
    public static final String SEED_BCRYPT =
            "$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW";

    /** 小美（房務）。 */
    public static final Actor HK1 = Actor.staff(ActorRole.HOUSEKEEPING, STAFF_HK1);
    /** 王主管（查房者）。 */
    public static final Actor MG1 = Actor.staff(ActorRole.MANAGER, STAFF_MG1);
    /** 林櫃檯（建單者）。 */
    public static final Actor FD1 = Actor.staff(ActorRole.FRONTDESK, STAFF_FD1);

    private TestData() {
    }
}
