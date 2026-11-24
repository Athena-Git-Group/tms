package com.athena.tms.bdd.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 讓代開單的開單交易在中途真的失敗，好驗收「一張開不成就整批回滾」，又不在產品碼留讓它失敗的開關。
 * 做法是在 tasks 表暫時裝一個寫入前觸發器，條件符合就丟例外；後端的整個請求交易因此回滾。
 * 工務單開單時也會先寫 tasks，所以同一種觸發器也擋得到工務單。
 *
 * <p>觸發器跨場景存在，清空業務資料的 TRUNCATE 也不會拆掉它，所以這個物件以場景為範圍，
 * 場景結束（含失敗）時由 Spring 呼叫 {@link #removeAll()} 拆掉本場景裝的每一個；
 * 場景開頭的重設另以 {@link #removeLeftovers(JdbcClient)} 拆掉任何殘留的。
 * 裝觸發器要表的擁有者權限，這裡的連線是測試容器的管理帳號，不是後端執行期用的帳號。
 */
public class DeskCreateFaults {

    private static final String PREFIX = "bdd_fault_";

    private final JdbcClient jdbcClient;
    private final List<String> installed = new ArrayList<>();

    public DeskCreateFaults(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void failTaskTitled(String title) {
        install("task_title_" + installed.size(), "NEW.title = " + literal(title));
    }

    public void failEveryTaskOf(long tenantId) {
        install("tenant_" + installed.size(), "NEW.tenant_id = " + tenantId);
    }

    public void removeAll() {
        for (String name : installed) {
            jdbcClient.sql("DROP TRIGGER IF EXISTS " + name + " ON tasks").update();
            jdbcClient.sql("DROP FUNCTION IF EXISTS " + name + "()").update();
        }
        installed.clear();
    }

    public static void removeLeftovers(JdbcClient jdbcClient) {
        List<Map<String, Object>> triggers = jdbcClient.sql("""
                SELECT t.tgname AS name, c.relname AS table_name
                  FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
                 WHERE t.tgname LIKE ?
                """).param(PREFIX + "%").query().listOfRows();
        for (Map<String, Object> trigger : triggers) {
            jdbcClient.sql("DROP TRIGGER IF EXISTS " + trigger.get("name") + " ON " + trigger.get("table_name")).update();
            jdbcClient.sql("DROP FUNCTION IF EXISTS " + trigger.get("name") + "()").update();
        }
    }

    private void install(String suffix, String condition) {
        String name = PREFIX + suffix;
        jdbcClient.sql("CREATE FUNCTION " + name + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF " + condition + " THEN RAISE EXCEPTION 'bdd: 這張單在測試裡被設定成開不出來'; END IF; "
                + "RETURN NEW; END $$").update();
        installed.add(name);
        jdbcClient.sql("CREATE TRIGGER " + name + " BEFORE INSERT ON tasks FOR EACH ROW EXECUTE FUNCTION "
                + name + "()").update();
    }

    private static String literal(String text) {
        return "'" + text.replace("'", "''") + "'";
    }
}
