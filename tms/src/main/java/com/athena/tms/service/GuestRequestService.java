/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  tools.jackson.databind.ObjectMapper
 */
package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.TooManyRequestsException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.GuestRatingRequest;
import com.athena.tms.dto.GuestRequestCreate;
import com.athena.tms.dto.GuestTaskView;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import com.athena.tms.service.ClassificationRuleEngine;
import com.athena.tms.service.GuestTenantConfigReader;
import com.athena.tms.service.GuestTextAdvisor;
import com.athena.tms.service.RoomRequestRateLimiter;
import com.athena.tms.service.TaskEngine;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class GuestRequestService {
    private static final Logger log = LoggerFactory.getLogger(GuestRequestService.class);
    private static final int TITLE_MAX_LENGTH = 120;
    private final TaskEngine taskEngine;
    private final TaskRepository taskRepository;
    private final GuestTenantConfigReader configReader;
    private final ClassificationRuleEngine ruleEngine;
    private final GuestTextAdvisor textAdvisor;
    private final RoomRequestRateLimiter roomRequestRateLimiter;
    private final ObjectMapper objectMapper;

    public GuestRequestService(TaskEngine taskEngine, TaskRepository taskRepository, GuestTenantConfigReader configReader, ClassificationRuleEngine ruleEngine, GuestTextAdvisor textAdvisor, RoomRequestRateLimiter roomRequestRateLimiter, ObjectMapper objectMapper) {
        this.taskEngine = taskEngine;
        this.taskRepository = taskRepository;
        this.configReader = configReader;
        this.ruleEngine = ruleEngine;
        this.textAdvisor = textAdvisor;
        this.roomRequestRateLimiter = roomRequestRateLimiter;
        this.objectMapper = objectMapper;
    }

    public GuestTaskView create(GuestRequestCreate request) {
        TaskCreateCommand command;
        boolean hasText;
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        boolean hasItem = request.itemId() != null && !request.itemId().isBlank();
        boolean bl = hasText = request.text() != null && !request.text().isBlank();
        if (hasItem == hasText) {
            throw new UnprocessableEntityException("itemId\uff08\u52fe\u9078\u55ae\uff09\u8207 text\uff08\u81ea\u7531\u6587\u5b57\uff09\u5fc5\u9808\u6070\u597d\u64c7\u4e00");
        }
        TaskCreateCommand taskCreateCommand = command = hasItem ? this.menuCommand(tenantId, actor, request.itemId().trim()) : this.freeTextCommand(tenantId, actor, request.text().trim(), request.lang());
        if (!this.roomRequestRateLimiter.tryAcquire(tenantId, actor.roomId())) {
            throw new TooManyRequestsException("\u540c\u4e00\u623f 10 \u5206\u9418\u5167\u6700\u591a 3 \u7b46\u9700\u6c42\uff0c\u8acb\u7a0d\u5f8c\u518d\u8a66");
        }
        try {
            return GuestTaskView.from(this.taskEngine.create(command));
        }
        catch (RuntimeException e) {
            this.roomRequestRateLimiter.release(tenantId, actor.roomId());
            throw e;
        }
    }

    public List<GuestTaskView> list() {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        return this.taskRepository.findByTenantIdAndReservationIdOrderByCreatedAtDesc(tenantId, actor.reservationId()).stream().map(GuestTaskView::from).toList();
    }

    public GuestTaskView cancel(UUID taskId) {
        long tenantId = TenantContext.require();
        this.ownedTask(tenantId, taskId);
        this.taskEngine.cancelByGuest(tenantId, taskId);
        return GuestTaskView.from(this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow());
    }

    public GuestTaskView rate(UUID taskId, GuestRatingRequest request) {
        long tenantId = TenantContext.require();
        if (request.rating() == null) {
            throw new UnprocessableEntityException("rating \u5fc5\u586b\uff081\u20135 \u6574\u6578\uff09");
        }
        this.ownedTask(tenantId, taskId);
        this.taskEngine.rate(tenantId, taskId, request.rating());
        return GuestTaskView.from(this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow());
    }

    private TaskCreateCommand menuCommand(long tenantId, CurrentActor actor, String itemId) {
        GuestTenantConfigReader.MenuItem item = this.configReader.requestMenu(tenantId).stream().filter(candidate -> candidate.key().equals(itemId)).findFirst().orElseThrow(() -> new UnprocessableEntityException("\u9078\u55ae\u88e1\u6c92\u6709\u9019\u500b\u9805\u76ee: " + itemId));
        return new TaskCreateCommand(tenantId, item.type(), "guest", actor.roomId(), actor.reservationId(), item.dept(), "pool", null, item.label(), this.noteJson(Map.of("menu_key", item.key())), null, null, GuestRequestService.workDateFor(item.type()), Actor.guest());
    }

    private TaskCreateCommand freeTextCommand(long tenantId, CurrentActor actor, String text, String lang) {
        ClassificationRuleEngine.Routing routing = this.ruleEngine.route(this.configReader.classificationRules(tenantId), text);
        LinkedHashMap<String, Object> noteFields = new LinkedHashMap<String, Object>();
        noteFields.put("text", text);
        if (lang != null && !lang.isBlank()) {
            noteFields.put("lang", lang.trim());
        }
        if (routing.ruleId() != null) {
            noteFields.put("rule_id", routing.ruleId());
        }
        this.textAdvisor.advise(tenantId, text, lang).ifPresent(advice -> {
            log.info("[llm-advice] tenant={} \u5efa\u8b70\u90e8\u9580={} \u7ffb\u8b6f={}\uff08\u50c5\u8a18\u9304\uff0c\u4e0d\u6539\u6b78\u5c6c\uff09", new Object[]{tenantId, advice.suggestedDept(), advice.translatedText()});
            if (advice.translatedText() != null) {
                noteFields.put("llm_translated", advice.translatedText());
            }
            if (advice.suggestedDept() != null) {
                noteFields.put("llm_suggested_dept", advice.suggestedDept());
            }
        });
        String title = text.length() <= 120 ? text : text.substring(0, 120) + "\u2026";
        return new TaskCreateCommand(tenantId, routing.type(), "guest", actor.roomId(), actor.reservationId(), routing.dept(), routing.initialState(), null, title, this.noteJson(noteFields), null, null, GuestRequestService.workDateFor(routing.type()), Actor.guest());
    }

    private static LocalDate workDateFor(String type) {
        return "cleaning".equals(type) ? LocalDate.now() : null;
    }

    private void ownedTask(long tenantId, UUID taskId) {
        Task task = this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow(() -> new NotFoundException("\u9700\u6c42\u4e0d\u5b58\u5728"));
        UUID sessionReservation = CurrentActor.require().reservationId();
        if (!sessionReservation.equals(task.getReservationId())) {
            throw new NotFoundException("\u9700\u6c42\u4e0d\u5b58\u5728");
        }
    }

    private String noteJson(Map<String, Object> fields) {
        return this.objectMapper.writeValueAsString(fields);
    }
}
