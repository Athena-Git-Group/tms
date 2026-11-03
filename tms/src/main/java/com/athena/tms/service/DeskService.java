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
                throw new UnprocessableEntityException("assign \u7684 dept \u503c\u57df\u50c5 housekeeping|engineering\uff08FR-021\uff09\uff0c\u6536\u5230: " + dept);
            }
            this.taskEngine.assignFromTriage(tenantId, taskId, dept, deskActor);
        } else if (ANSWER.equals(request.action())) {
            if (request.note() == null || request.note().isBlank()) {
                throw new UnprocessableEntityException("answer \u5fc5\u9644 note\uff08\u56de\u8986\u5167\u5bb9\uff0c\u4f4f\u5ba2\u7aef\u53ef\u898b\uff09");
            }
            this.taskEngine.answer(tenantId, taskId, request.note(), deskActor);
        } else {
            throw new UnprocessableEntityException("action \u50c5 assign|answer\uff08\u4e09\u9078\u4e00\u7684\u7b2c\u4e09\u9078\uff1danswer \u7576\u5834\u56de\u7b54\uff09\uff0c\u6536\u5230: " + request.action());
        }
        return this.requireTask(tenantId, taskId);
    }

    private Task requireTask(long tenantId, UUID taskId) {
        return this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow(() -> new NotFoundException("\u67e5\u7121\u4efb\u52d9: " + String.valueOf(taskId)));
    }
}
