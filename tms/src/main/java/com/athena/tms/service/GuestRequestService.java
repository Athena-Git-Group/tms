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
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final PushNotifier pushNotifier;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public GuestRequestService(TaskEngine taskEngine, TaskRepository taskRepository, GuestTenantConfigReader configReader, ClassificationRuleEngine ruleEngine, GuestTextAdvisor textAdvisor, RoomRequestRateLimiter roomRequestRateLimiter, ObjectMapper objectMapper, PushNotifier pushNotifier, JdbcTemplate jdbcTemplate, Clock clock) {
        this.taskEngine = taskEngine;
        this.taskRepository = taskRepository;
        this.configReader = configReader;
        this.ruleEngine = ruleEngine;
        this.textAdvisor = textAdvisor;
        this.roomRequestRateLimiter = roomRequestRateLimiter;
        this.objectMapper = objectMapper;
        this.pushNotifier = pushNotifier;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public GuestTaskView create(GuestRequestCreate request) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        boolean hasItem = request.itemId() != null && !request.itemId().isBlank();
        boolean hasText = request.text() != null && !request.text().isBlank();
        if (hasItem == hasText) {
            throw new UnprocessableEntityException("itemId（勾選單）與 text（自由文字）必須恰好擇一");
        }
        TaskCreateCommand command = hasItem
                ? this.menuCommand(tenantId, actor, request.itemId().trim(), request.quantity())
                : this.freeTextCommand(tenantId, actor, request.text().trim(), request.lang());
        if (!this.roomRequestRateLimiter.tryAcquire(tenantId, actor.roomId())) {
            throw new TooManyRequestsException("同一房 10 分鐘內最多 3 筆需求，請洽櫃檯協助");
        }
        try {
            Task task = this.taskEngine.create(command);
            if ("pool".equals(task.getState())) {
                this.markGuestScanPoolSource(tenantId, task.getId());
                this.pushNotifier.notifyPoolNewRequest(tenantId, task.getId());
            }
            return GuestTaskView.from(task);
        }
        catch (RuntimeException e) {
            this.roomRequestRateLimiter.release(tenantId, actor.roomId());
            throw e;
        }
    }

    private void markGuestScanPoolSource(long tenantId, UUID taskId) {
        this.jdbcTemplate.update(
                "UPDATE tasks SET pool_source = COALESCE(pool_source, 'guest_scan') WHERE tenant_id = ? AND id = ?",
                tenantId, taskId);
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
        this.pushNotifier.notifyGuestWithdrew(tenantId, taskId);
        return GuestTaskView.from(this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow());
    }

    public GuestTaskView rate(UUID taskId, GuestRatingRequest request) {
        long tenantId = TenantContext.require();
        if (request.rating() == null) {
            throw new UnprocessableEntityException("rating 必填（1–5 整數）");
        }
        this.ownedTask(tenantId, taskId);
        this.taskEngine.rate(tenantId, taskId, request.rating());
        return GuestTaskView.from(this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow());
    }

    private TaskCreateCommand menuCommand(long tenantId, CurrentActor actor, String itemId, Integer quantity) {
        GuestTenantConfigReader.MenuItem item = this.configReader.requestMenu(tenantId).stream().filter(candidate -> candidate.key().equals(itemId)).findFirst().orElse(null);
        if (item == null) {
            return this.freeTextCommand(tenantId, actor, itemId, null);
        }
        int effectiveQuantity = quantity != null && quantity > 0 ? quantity : 1;
        String title = effectiveQuantity > 1 ? item.label() + " ×" + effectiveQuantity : item.label();
        return this.guestCommand(tenantId, actor, item.type(), item.dept(), "pool", title, this.noteJson(Map.of("menu_key", item.key(), "quantity", effectiveQuantity)));
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
            log.info("[llm-advice] tenant={} 建議部門={} 翻譯={}（僅記錄，不改歸屬）", new Object[]{tenantId, advice.suggestedDept(), advice.translatedText()});
            if (advice.translatedText() != null) {
                noteFields.put("llm_translated", advice.translatedText());
            }
            if (advice.suggestedDept() != null) {
                noteFields.put("llm_suggested_dept", advice.suggestedDept());
            }
        });
        String title = text.length() <= TITLE_MAX_LENGTH ? text : text.substring(0, TITLE_MAX_LENGTH) + "…";
        return this.guestCommand(tenantId, actor, routing.type(), routing.dept(), routing.initialState(), title, this.noteJson(noteFields));
    }

    // 住客不直通工務：工務單要有報修人，給工務或修繕的需求一律先到待派單，由櫃檯派給工務時開單。
    private TaskCreateCommand guestCommand(long tenantId, CurrentActor actor, String type, String dept, String initialState, String title, String note) {
        if ("engineering".equals(dept) || "repair".equals(type)) {
            return this.guestCommand(tenantId, actor, "other", "frontdesk", "triage", title, note);
        }
        return new TaskCreateCommand(tenantId, type, "guest", actor.roomId(), actor.reservationId(), dept, initialState, null, title, note, null, null, this.workDateFor(type), Actor.guest());
    }

    private LocalDate workDateFor(String type) {
        return "cleaning".equals(type) ? LocalDate.now(this.clock) : null;
    }

    private void ownedTask(long tenantId, UUID taskId) {
        Task task = this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow(() -> new NotFoundException("需求不存在"));
        CurrentActor actor = CurrentActor.require();
        if (actor.reservationId().equals(task.getReservationId())) {
            return;
        }
        if (task.getReservationId() == null && actor.roomId() != null && actor.roomId().equals(task.getRoomId())) {
            return;
        }
        throw new NotFoundException("需求不存在");
    }

    private String noteJson(Map<String, Object> fields) {
        return this.objectMapper.writeValueAsString(fields);
    }
}
