package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.codes.SopAppliesTo;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.entity.SopItem;
import com.athena.tms.entity.Task;
import com.athena.tms.entity.TaskSopCheck;
import com.athena.tms.dao.SopItemRepository;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dao.TaskSopCheckRepository;
import com.athena.tms.dto.Actor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.athena.tms.dto.SopCheckRequest;

/**
 * 員工待接池的應用服務：查池、認領、兩段回報、SOP 勾記。
 *
 * <p>任務狀態一律交給 TaskEngine 變更，本類自己不動狀態。引擎把狀態、部門、
 * 操作者這些前提條件直接寫在 UPDATE 的 WHERE 裡，條件不符就一列也不改。
 * 若改成先查詢再更新，查與改之間資料可能被別的請求改掉，查到的判斷就過期了。
 * 條件不符一律回 409，不區分「單不存在」與「狀態不符」。因此凡是要回 404 的
 * 情境（跨租戶或任務不存在），都必須在呼叫引擎前先以租戶範圍的查詢判定。
 *
 * <p>本類只做三件事：呼叫引擎前的 404 前置判定、把 CurrentActor 轉成引擎的
 * Actor，以及寫入 SOP 勾記表。勾記表是從事件紀錄整理出來、方便查詢的
 * 目前狀態副本，權威紀錄在 task_events。
 */
@Service
public class StaffTaskService {

    private final TaskEngine taskEngine;
    private final TaskRepository taskRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final SopItemRepository sopItemRepository;
    private final TaskSopCheckRepository taskSopCheckRepository;

    public StaffTaskService(TaskEngine taskEngine,
                            TaskRepository taskRepository,
                            StaffAccountRepository staffAccountRepository,
                            SopItemRepository sopItemRepository,
                            TaskSopCheckRepository taskSopCheckRepository) {
        this.taskEngine = taskEngine;
        this.taskRepository = taskRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.sopItemRepository = sopItemRepository;
        this.taskSopCheckRepository = taskSopCheckRepository;
    }

    /**
     * 只回本部門處於 pool 狀態的任務。部門取自認證脈絡的資料庫當下值而不是
     * JWT claim，也不收查詢參數，否則就得防範用參數指定別部門來窺探他人的池。
     * 無部門的帳號回 422 而不是空列表，因為空列表會被誤讀成「現在沒單」。
     */
    public List<Task> pool() {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        if (actor.dept() == null) {
            throw new UnprocessableEntityException("此帳號未設部門，沒有對應的部門待接池（FR-010）");
        }
        return taskRepository.findByTenantIdAndDeptAndStateOrderByCreatedAtAsc(
                tenantId, actor.dept(), TaskState.POOL);
    }

    /**
     * 認領，限同部門且任務仍在 pool。這兩個前提刻意不在這裡預檢：兩人同時認領時
     * 可能都通過預檢，所以依類別註解的原則交給引擎的條件式更新一次驗證。
     * 失敗後才回頭查認領者，因為錯誤訊息要能講出「這單已被誰認領」的人名，
     * 而引擎失敗時只知道一列都沒改到。這次查詢的結果只進訊息、不做任何判斷，
     * 所以即使查到的又是過期資料，也不會導致錯誤的決定。
     */
    public Task claim(UUID taskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        requireTask(tenantId, taskId);
        try {
            taskEngine.claim(tenantId, taskId, actor.dept(), staffActor(actor));
        } catch (StateConflictException conflict) {
            throw enrichClaimConflict(tenantId, taskId, conflict);
        }
        return requireTask(tenantId, taskId);
    }

    /** 兩段回報的前半：accepted 到 doing。引擎的條件式更新同句驗證操作者是認領者本人，並記錄 started_at。 */
    public Task start(UUID taskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        requireTask(tenantId, taskId);
        taskEngine.start(tenantId, taskId, staffActor(actor));
        return requireTask(tenantId, taskId);
    }

    /**
     * 兩段回報的後半：doing 到 done。引擎的條件式更新同句驗證操作者是認領者本人，
     * 並記錄 done_at。實際耗時是 done_at 減 started_at 的衍生值，不另存欄位。
     * SOP 有跳過項不擋完成，這裡刻意不做任何 SOP 完整度檢查。
     */
    public Task complete(UUID taskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        requireTask(tenantId, taskId);
        taskEngine.complete(tenantId, taskId, staffActor(actor));
        return requireTask(tenantId, taskId);
    }

    /**
     * SOP 勾選或跳過。checked 與 skipped 必須恰取其一，同真或同缺都回 422。
     * label 抄寫 sop_items 的當下值做快照，項目日後改名不影響歷史單。
     * 重勾同一項依唯一鍵 uq_task_sop_checks_item 覆寫。本表只是類別註解所說的
     * 查詢用副本，「誰在幾點動過哪一項」的完整歷史在 task_events 的
     * sop_checked 事件。
     *
     * <p>事件寫入走 TaskEngine.sopCheck，因為事件 seq 要對 tasks.event_seq
     * 原子取號，而更新 tasks 依類別註解的原則只有引擎有權做。
     * 這裡只負責這份副本的 upsert。
     */
    public TaskSopCheck sopCheck(UUID taskId, String itemKey, SopCheckRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        boolean checked = Boolean.TRUE.equals(request.checked());
        boolean skipped = Boolean.TRUE.equals(request.skipped());
        if (checked == skipped) {
            throw new UnprocessableEntityException(
                    "checked 與 skipped 必須恰取其一（FR-013：{\"checked\":true} 或 {\"skipped\":true}）");
        }
        requireTask(tenantId, taskId);
        // 不過濾 active：項目在建單後才下架時，依快照語意歷史單仍可補記。
        SopItem item = sopItemRepository
                .findByTenantIdAndAppliesToAndItemKey(tenantId, SopAppliesTo.CLEANING, itemKey)
                .orElseThrow(() -> new NotFoundException("查無 SOP 項目: " + itemKey));

        taskEngine.sopCheck(tenantId, taskId, item.getItemKey(), item.getLabel(),
                checked, skipped, staffActor(actor));

        TaskSopCheck check = taskSopCheckRepository
                .findByTenantIdAndTaskIdAndItemKey(tenantId, taskId, itemKey)
                .orElseGet(() -> {
                    TaskSopCheck fresh = new TaskSopCheck();
                    fresh.setId(UuidV7.generate());
                    fresh.setTenantId(tenantId);
                    fresh.setTaskId(taskId);
                    fresh.setItemKey(item.getItemKey());
                    return fresh;
                });
        check.setLabel(item.getLabel());
        check.setChecked(checked);
        check.setSkipped(skipped);
        check.setCheckedBy(actor.staffId());
        check.setCheckedAt(OffsetDateTime.now());
        return taskSopCheckRepository.save(check);
    }

    /** 以租戶範圍的查詢判 404：跨租戶與不存在一律回 not_found，不洩漏任務是否存在。 */
    private Task requireTask(long tenantId, UUID taskId) {
        return taskRepository.findByTenantIdAndId(tenantId, taskId)
                .orElseThrow(() -> new NotFoundException("查無任務: " + taskId));
    }

    private static Actor staffActor(CurrentActor actor) {
        return Actor.staff(actor.role(), actor.staffId());
    }

    /**
     * 把「單已被認領」的認領失敗改寫成帶認領者姓名的訊息，例如「這單已被小美認領」。
     * 只有確認任務已在別人手上才改寫。其他原因的失敗（跨部門、狀態不符）原樣拋回，
     * 錯誤碼一律維持 state_conflict。
     */
    private StateConflictException enrichClaimConflict(
            long tenantId, UUID taskId, StateConflictException original) {
        Task current = taskRepository.findByTenantIdAndId(tenantId, taskId).orElse(null);
        if (current != null && current.getAssigneeId() != null
                && !TaskState.POOL.equals(current.getState())) {
            String claimerName = staffAccountRepository
                    .findByTenantIdAndId(tenantId, current.getAssigneeId())
                    .map(account -> account.getName())
                    .orElse(null);
            if (claimerName != null) {
                return new StateConflictException("這單已被" + claimerName + "認領");
            }
        }
        return original;
    }
}
