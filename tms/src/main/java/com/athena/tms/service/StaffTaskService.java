/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 */
package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.SopItemRepository;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dao.TaskSopCheckRepository;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.SopCheckRequest;
import com.athena.tms.entity.SopItem;
import com.athena.tms.entity.Task;
import com.athena.tms.entity.TaskSopCheck;
import com.athena.tms.service.TaskEngine;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StaffTaskService {
    private final TaskEngine taskEngine;
    private final TaskRepository taskRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final SopItemRepository sopItemRepository;
    private final TaskSopCheckRepository taskSopCheckRepository;

    public StaffTaskService(TaskEngine taskEngine, TaskRepository taskRepository, StaffAccountRepository staffAccountRepository, SopItemRepository sopItemRepository, TaskSopCheckRepository taskSopCheckRepository) {
        this.taskEngine = taskEngine;
        this.taskRepository = taskRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.sopItemRepository = sopItemRepository;
        this.taskSopCheckRepository = taskSopCheckRepository;
    }

    public List<Task> pool() {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        if (actor.dept() == null) {
            throw new UnprocessableEntityException("\u6b64\u5e33\u865f\u672a\u8a2d\u90e8\u9580\uff0c\u6c92\u6709\u5c0d\u61c9\u7684\u90e8\u9580\u5f85\u63a5\u6c60\uff08FR-010\uff09");
        }
        return this.taskRepository.findByTenantIdAndDeptAndStateOrderByCreatedAtAsc(tenantId, actor.dept(), "pool");
    }

    public Task claim(UUID taskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        this.requireTask(tenantId, taskId);
        try {
            this.taskEngine.claim(tenantId, taskId, actor.dept(), StaffTaskService.staffActor(actor));
        }
        catch (StateConflictException conflict) {
            throw this.enrichClaimConflict(tenantId, taskId, conflict);
        }
        return this.requireTask(tenantId, taskId);
    }

    public Task start(UUID taskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        this.requireTask(tenantId, taskId);
        this.taskEngine.start(tenantId, taskId, StaffTaskService.staffActor(actor));
        return this.requireTask(tenantId, taskId);
    }

    public Task complete(UUID taskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        this.requireTask(tenantId, taskId);
        this.taskEngine.complete(tenantId, taskId, StaffTaskService.staffActor(actor));
        return this.requireTask(tenantId, taskId);
    }

    public TaskSopCheck sopCheck(UUID taskId, String itemKey, SopCheckRequest request) {
        boolean skipped;
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        boolean checked = Boolean.TRUE.equals(request.checked());
        if (checked == (skipped = Boolean.TRUE.equals(request.skipped()))) {
            throw new UnprocessableEntityException("checked \u8207 skipped \u5fc5\u9808\u6070\u53d6\u5176\u4e00\uff08FR-013\uff1a{\"checked\":true} \u6216 {\"skipped\":true}\uff09");
        }
        this.requireTask(tenantId, taskId);
        SopItem item = this.sopItemRepository.findByTenantIdAndAppliesToAndItemKey(tenantId, "cleaning", itemKey).orElseThrow(() -> new NotFoundException("\u67e5\u7121 SOP \u9805\u76ee: " + itemKey));
        this.taskEngine.sopCheck(tenantId, taskId, item.getItemKey(), item.getLabel(), checked, skipped, StaffTaskService.staffActor(actor));
        TaskSopCheck check = this.taskSopCheckRepository.findByTenantIdAndTaskIdAndItemKey(tenantId, taskId, itemKey).orElseGet(() -> {
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
        return (TaskSopCheck)this.taskSopCheckRepository.save(check);
    }

    private Task requireTask(long tenantId, UUID taskId) {
        return this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow(() -> new NotFoundException("\u67e5\u7121\u4efb\u52d9: " + String.valueOf(taskId)));
    }

    private static Actor staffActor(CurrentActor actor) {
        return Actor.staff(actor.role(), actor.staffId());
    }

    private StateConflictException enrichClaimConflict(long tenantId, UUID taskId, StateConflictException original) {
        String claimerName;
        Task current = this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElse(null);
        if (current != null && current.getAssigneeId() != null && !"pool".equals(current.getState()) && (claimerName = (String)this.staffAccountRepository.findByTenantIdAndId(tenantId, current.getAssigneeId()).map(account -> account.getName()).orElse(null)) != null) {
            return new StateConflictException("\u9019\u55ae\u5df2\u88ab" + claimerName + "\u8a8d\u9818");
        }
        return original;
    }
}
