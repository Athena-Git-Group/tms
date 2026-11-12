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
            // 不在勾選單上的品項當自由文字處理：走規則引擎，沒命中就進 triage 由櫃檯分流，
            // 不因為 App 與勾選單版本不同步就直接拒絕住客。
            return this.freeTextCommand(tenantId, actor, itemId, null);
        }
        int effectiveQuantity = quantity != null && quantity > 0 ? quantity : 1;
        String title = effectiveQuantity > 1 ? item.label() + " ×" + effectiveQuantity : item.label();
        return new TaskCreateCommand(tenantId, item.type(), "guest", actor.roomId(), actor.reservationId(), item.dept(), "pool", null, title, this.noteJson(Map.of("menu_key", item.key(), "quantity", effectiveQuantity)), null, null, this.workDateFor(item.type()), Actor.guest());
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
        return new TaskCreateCommand(tenantId, routing.type(), "guest", actor.roomId(), actor.reservationId(), routing.dept(), routing.initialState(), null, title, this.noteJson(noteFields), null, null, this.workDateFor(routing.type()), Actor.guest());
    }

    /** 住客要求的清潔歸在「今天」的工作日，「今天」以注入的時鐘為準，與班別、跨日判定同一個來源。 */
    private LocalDate workDateFor(String type) {
        return "cleaning".equals(type) ? LocalDate.now(this.clock) : null;
    }

    private void ownedTask(long tenantId, UUID taskId) {
        Task task = this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow(() -> new NotFoundException("需求不存在"));
        CurrentActor actor = CurrentActor.require();
        if (actor.reservationId().equals(task.getReservationId())) {
            return;
        }
        // 櫃檯代住客轉的單沒有綁訂單（reservation_id 為空）：這種單以「同一間房」認定
        // 歸屬——session 綁定的房間跟任務的房間一致就視為這位住客的需求。
        if (task.getReservationId() == null && actor.roomId() != null && actor.roomId().equals(task.getRoomId())) {
            return;
        }
        throw new NotFoundException("需求不存在");
    }

    private String noteJson(Map<String, Object> fields) {
        return this.objectMapper.writeValueAsString(fields);
    }
}
