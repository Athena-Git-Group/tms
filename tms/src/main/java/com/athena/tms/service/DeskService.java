/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.persistence.EntityManager
 *  org.springframework.stereotype.Service
 */
package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TriageDecisionRequest;
import com.athena.tms.entity.Task;
import com.athena.tms.service.TaskEngine;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeskService {
    private static final String ASSIGN = "assign";
    private static final String ANSWER = "answer";
    private final EntityManager entityManager;
    private final TaskEngine taskEngine;
    private final TaskRepository taskRepository;

    public DeskService(EntityManager entityManager, TaskEngine taskEngine, TaskRepository taskRepository) {
        this.entityManager = entityManager;
        this.taskEngine = taskEngine;
        this.taskRepository = taskRepository;
    }

    public List<Task> triageList() {
        long tenantId = TenantContext.require();
        return this.entityManager.createQuery("SELECT t FROM Task t\nWHERE t.tenantId = :tenantId AND t.state = :state\nORDER BY t.createdAt ASC\n", Task.class).setParameter("tenantId", (Object)tenantId).setParameter("state", (Object)"triage").getResultList();
    }

    public List<Task> storeTasks() {
        long tenantId = TenantContext.require();
        return this.entityManager.createQuery("SELECT t FROM Task t\nWHERE t.tenantId = :tenantId\nORDER BY t.createdAt DESC\n", Task.class).setParameter("tenantId", (Object)tenantId).getResultList();
    }

    public Task decide(UUID taskId, TriageDecisionRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        this.requireTask(tenantId, taskId);
        Actor deskActor = Actor.staff(actor.role(), actor.staffId());
        if (ASSIGN.equals(request.action())) {
            String dept = request.dept();
            if (!"housekeeping".equals(dept) && !"engineering".equals(dept)) {
                throw new UnprocessableEntityException("assign 的 dept 值域僅 housekeeping|engineering（FR-021），收到: " + dept);
            }
            this.taskEngine.assignFromTriage(tenantId, taskId, dept, deskActor);
        } else if (ANSWER.equals(request.action())) {
            if (request.note() == null || request.note().isBlank()) {
                throw new UnprocessableEntityException("answer 必附 note（回覆內容，住客端可見）");
            }
            this.taskEngine.answer(tenantId, taskId, request.note(), deskActor);
        } else {
            throw new UnprocessableEntityException("action 僅 assign|answer（三選一的第三選＝answer 當場回答），收到: " + request.action());
        }
        return this.requireTask(tenantId, taskId);
    }

    private Task requireTask(long tenantId, UUID taskId) {
        return this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow(() -> new NotFoundException("查無任務: " + String.valueOf(taskId)));
    }
}
