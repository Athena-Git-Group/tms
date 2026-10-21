package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.common.codes.Dept;
import com.athena.tms.common.codes.TaskSource;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.common.codes.TaskType;
import com.athena.tms.entity.Task;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.service.TaskEngine;

import java.util.UUID;

import static com.athena.tms.integration.stepdefs.utils.TestData.FD1;
import static com.athena.tms.integration.stepdefs.utils.TestData.HK1;
import static com.athena.tms.integration.stepdefs.utils.TestData.RESERVATION_B001;
import static com.athena.tms.integration.stepdefs.utils.TestData.ROOM_305;
import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;

/**
 * 任務測試資料一律經由本工具建立，為的是強制走 {@link TaskEngine}，不直接 INSERT tasks 資料表。
 * TaskEngine 是任務狀態變更的唯一入口，走它才會同步產生事件序號與 outbox 訊息；
 * 直插的資料缺這兩樣，依賴事件或推播的斷言會莫名失敗。
 *
 * <p>所有呼叫都包在 {@link Tenants#asTenant} 裡，否則列級安全（RLS）會讓查詢只回零列。
 *
 * <p>共用方法一律建 amenity（備品）單。打掃單受「同一房同一天只能有一張未完成」
 * 的資料庫約束，共用方法若建打掃單，場景之間會互相撞到這條限制。
 * 需要打掃單的場景自行建立，並錯開房間或日期。
 */
public class TaskFixtures {

    private final TaskEngine engine;

    public TaskFixtures(TaskEngine engine) {
        this.engine = engine;
    }

    public TaskEngine engine() {
        return engine;
    }

    public Task newPoolTask(String title) {
        return Tenants.asTenant(TENANT_1, () -> engine.create(new TaskCreateCommand(
                TENANT_1, TaskType.AMENITY, TaskSource.DESK, ROOM_305, RESERVATION_B001,
                Dept.HOUSEKEEPING, TaskState.POOL, null, title,
                null, null, null, null, FD1)));
    }

    /**
     * 完成後任務的事件序號停在 3：建單佔 1、認領佔 2、開工佔 3。
     * 斷言事件序號或事件數的場景以此為基準。
     */
    public void claimAndStartAsHk1(UUID taskId) {
        Tenants.runAsTenant(TENANT_1, () -> {
            engine.claim(TENANT_1, taskId, Dept.HOUSEKEEPING, HK1);
            engine.start(TENANT_1, taskId, HK1);
        });
    }

    /**
     * 把任務從建單一路推到完成。完成後事件序號停在 4，
     * 之後的動作（例如查房、評分）從 5 起算。
     */
    public UUID newDoneTask(String title) {
        Task task = newPoolTask(title);
        Tenants.runAsTenant(TENANT_1, () -> {
            engine.claim(TENANT_1, task.getId(), Dept.HOUSEKEEPING, HK1);
            engine.start(TENANT_1, task.getId(), HK1);
            engine.complete(TENANT_1, task.getId(), HK1);
        });
        return task.getId();
    }
}
