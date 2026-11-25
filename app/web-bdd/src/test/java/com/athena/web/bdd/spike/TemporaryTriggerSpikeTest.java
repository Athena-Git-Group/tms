package com.athena.web.bdd.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.GuestSeed;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 代開單的驗收要讓「開單交易做到一半失敗」真的發生，又不能在產品碼留讓它失敗的開關。
 * 這支測試先確認櫃檯 Web 測試線連的資料庫帳號能裝、拆暫時觸發器，而且觸發器一丟例外，後端整個請求回滾。
 *
 * <p>會清空 5433 資料庫的業務資料，要 8080 後端開著；和其他 spike 一樣被 pom 排除在平常的 mvn test 之外。
 * 跑法：{@code mvn -q test -Dtest=TemporaryTriggerSpikeTest}。
 */
class TemporaryTriggerSpikeTest {

    private static final String ROOM = "311";
    private static final String FAILING_TITLE = "spike 這張開不出來";
    private static final String TICKET_TRIGGER = "bdd_fault_repair_ticket";
    private static final String TITLE_TRIGGER = "bdd_fault_task_title";

    private final BackendChannel channel = new BackendChannel();
    private final String dbUrl = System.getProperty("backend.db.url", "jdbc:postgresql://127.0.0.1:5433/tms");
    private final String dbUser = System.getProperty("backend.db.user", "tms");
    private final String dbPassword = System.getProperty("backend.db.password", "tms");

    @AfterEach
    void dropTriggers() throws Exception {
        sql("DROP TRIGGER IF EXISTS " + TICKET_TRIGGER + " ON repair_tickets",
                "DROP FUNCTION IF EXISTS " + TICKET_TRIGGER + "()",
                "DROP TRIGGER IF EXISTS " + TITLE_TRIGGER + " ON tasks",
                "DROP FUNCTION IF EXISTS " + TITLE_TRIGGER + "()");
        assertEquals(0, count("SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'bdd_fault_%'"),
                "場景結束後不留任何暫時觸發器");
    }

    @Test
    void 開工務單的觸發器讓派給工務整個回滾() throws Exception {
        channel.resetBusinessData();
        CreatedStaff clerk = channel.ensureFrontdeskDefaults().staff("林佩君");
        channel.ensureGuestDefaults();
        long tenantId = channel.ensureFrontdeskDefaults().tenantId();
        System.out.printf("[spike T002] 連線帳號 %s，superuser=%s，tasks 有 TRIGGER 權限=%s%n", dbUser,
                scalar("SELECT rolsuper::text FROM pg_roles WHERE rolname = current_user"),
                scalar("SELECT has_table_privilege(current_user, 'tasks', 'TRIGGER')::text"));

        UUID taskId = channel.seedGuestRequest(ROOM, new GuestSeed(GuestSeed.Situation.TRIAGE,
                "spike 冷氣不冷", "other", channel.backendNow().minusMinutes(1), null, null, null, null));
        long eventsBefore = count("SELECT count(*) FROM task_events WHERE task_id = '" + taskId + "'");
        long outboxBefore = count("SELECT count(*) FROM outbox WHERE tenant_id = " + tenantId);
        long counterBefore = count("SELECT coalesce(max(last_no), 0) FROM repair_ticket_counters WHERE tenant_id = " + tenantId);

        sql("CREATE FUNCTION " + TICKET_TRIGGER + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                        + "IF NEW.tenant_id = " + tenantId + " THEN RAISE EXCEPTION 'spike: 開工務單失敗'; END IF; "
                        + "RETURN NEW; END $$",
                "CREATE TRIGGER " + TICKET_TRIGGER + " BEFORE INSERT ON repair_tickets FOR EACH ROW EXECUTE FUNCTION "
                        + TICKET_TRIGGER + "()");

        IllegalStateException failed = assertThrows(IllegalStateException.class,
                () -> channel.deskAssign(taskId, clerk, "engineering", null));
        System.out.println("[spike T002] 觸發器在場時派給工務：" + failed.getMessage());
        assertTrue(failed.getMessage().contains("HTTP 5"), "後端回 5xx");
        assertEquals("triage", channel.taskRow(taskId).get("state"), "任務仍在待派單");
        assertEquals(0, count("SELECT count(*) FROM repair_tickets WHERE task_id = '" + taskId + "'"));
        assertEquals(eventsBefore, count("SELECT count(*) FROM task_events WHERE task_id = '" + taskId + "'"));
        assertEquals(outboxBefore, count("SELECT count(*) FROM outbox WHERE tenant_id = " + tenantId));
        assertEquals(counterBefore,
                count("SELECT coalesce(max(last_no), 0) FROM repair_ticket_counters WHERE tenant_id = " + tenantId));

        dropTriggers();
        channel.deskAssign(taskId, clerk, "engineering", null);
        assertEquals("pool", channel.taskRow(taskId).get("state"), "拆掉觸發器之後照常派出");
        assertEquals(1, count("SELECT count(*) FROM repair_tickets WHERE task_id = '" + taskId + "'"));
    }

    @Test
    void 標題觸發器只擋寫入那個標題的任務() throws Exception {
        channel.resetBusinessData();
        channel.ensureFrontdeskDefaults();
        channel.ensureGuestDefaults();
        sql("CREATE FUNCTION " + TITLE_TRIGGER + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                        + "IF NEW.title = '" + FAILING_TITLE + "' THEN RAISE EXCEPTION 'spike: 這個標題開不出來'; END IF; "
                        + "RETURN NEW; END $$",
                "CREATE TRIGGER " + TITLE_TRIGGER + " BEFORE INSERT ON tasks FOR EACH ROW EXECUTE FUNCTION "
                        + TITLE_TRIGGER + "()");

        UUID other = channel.seedGuestRequest(ROOM, new GuestSeed(GuestSeed.Situation.HOUSEKEEPING_POOL,
                "spike 別的標題", "amenity", channel.backendNow().minusMinutes(1), null, null, null, null));
        assertEquals("pool", channel.taskRow(other).get("state"));
        RuntimeException blocked = assertThrows(RuntimeException.class,
                () -> channel.seedGuestRequest(ROOM, new GuestSeed(GuestSeed.Situation.HOUSEKEEPING_POOL,
                        FAILING_TITLE, "amenity", channel.backendNow().minusMinutes(1), null, null, null, null)));
        System.out.println("[spike T002] 標題觸發器擋下：" + rootMessage(blocked));
        assertEquals(0, count("SELECT count(*) FROM tasks WHERE title = '" + FAILING_TITLE + "'"));
    }

    @Test
    void 清空業務資料不會拆掉觸發器_所以清理要自己拆() throws Exception {
        sql("CREATE FUNCTION " + TITLE_TRIGGER + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END $$",
                "CREATE TRIGGER " + TITLE_TRIGGER + " BEFORE INSERT ON tasks FOR EACH ROW EXECUTE FUNCTION "
                        + TITLE_TRIGGER + "()");
        channel.resetBusinessData();
        assertEquals(1, count("SELECT count(*) FROM pg_trigger WHERE tgname = '" + TITLE_TRIGGER + "'"));
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    private void sql(String... statements) throws Exception {
        try (Connection db = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                Statement statement = db.createStatement()) {
            for (String each : statements) {
                statement.execute(each);
            }
        }
    }

    private String scalar(String query) throws Exception {
        try (Connection db = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                Statement statement = db.createStatement();
                ResultSet row = statement.executeQuery(query)) {
            row.next();
            return row.getString(1);
        }
    }

    private long count(String query) throws Exception {
        return Long.parseLong(scalar(query));
    }
}
