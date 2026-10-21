package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.codes.TaskSource;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.common.codes.TaskType;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.TooManyRequestsException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.entity.Task;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.service.ClassificationRuleEngine.Routing;
import com.athena.tms.service.GuestTenantConfigReader.MenuItem;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.athena.tms.dto.GuestRatingRequest;
import com.athena.tms.dto.GuestRequestCreate;
import com.athena.tms.dto.GuestTaskView;

/**
 * 住客需求的建立、查詢、收回、評分。所有狀態變更一律經
 * TaskEngine 這個唯一入口；本類的職責只是把住客視角翻譯成引擎指令：
 * 選單項→部門映射、自由文字→規則引擎、加上住客專屬的限流與歸屬檢查。
 *
 * <p>歸屬檢查用住客工作階段綁的 reservation_id 而非房號：
 * 房號跨住客存在，訂單不會——按房撈會讓新住客看到、甚至動到前一位住客的單。
 * 不屬於本訂單的任務 id 一律 404 而非 403，不洩漏存在性。
 */
@Service
public class GuestRequestService {

    private static final Logger log = LoggerFactory.getLogger(GuestRequestService.class);

    /** 標題是看板欄位、要一眼掃完；原文再長都完整留在 note，不丟資訊。 */
    private static final int TITLE_MAX_LENGTH = 120;

    private final TaskEngine taskEngine;
    private final TaskRepository taskRepository;
    private final GuestTenantConfigReader configReader;
    private final ClassificationRuleEngine ruleEngine;
    private final GuestTextAdvisor textAdvisor;
    private final RoomRequestRateLimiter roomRequestRateLimiter;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public GuestRequestService(TaskEngine taskEngine,
                               TaskRepository taskRepository,
                               GuestTenantConfigReader configReader,
                               ClassificationRuleEngine ruleEngine,
                               GuestTextAdvisor textAdvisor,
                               RoomRequestRateLimiter roomRequestRateLimiter,
                               tools.jackson.databind.ObjectMapper objectMapper) {
        this.taskEngine = taskEngine;
        this.taskRepository = taskRepository;
        this.configReader = configReader;
        this.ruleEngine = ruleEngine;
        this.textAdvisor = textAdvisor;
        this.roomRequestRateLimiter = roomRequestRateLimiter;
        this.objectMapper = objectMapper;
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
                ? menuCommand(tenantId, actor, request.itemId().trim())
                : freeTextCommand(tenantId, actor, request.text().trim(), request.lang());

        // 名額檢查放在輸入驗證之後：422 的爛請求不該吃掉住客的 3 筆名額
        if (!roomRequestRateLimiter.tryAcquire(tenantId, actor.roomId())) {
            throw new TooManyRequestsException("同一房 10 分鐘內最多 3 筆需求，請稍後再試");
        }
        try {
            return GuestTaskView.from(taskEngine.create(command));
        } catch (RuntimeException e) {
            // 限流只數「成功建立」：引擎拒單（422 等）要退還先佔的名額
            roomRequestRateLimiter.release(tenantId, actor.roomId());
            throw e;
        }
    }

    public List<GuestTaskView> list() {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        return taskRepository
                .findByTenantIdAndReservationIdOrderByCreatedAtDesc(tenantId, actor.reservationId())
                .stream().map(GuestTaskView::from).toList();
    }

    public GuestTaskView cancel(UUID taskId) {
        long tenantId = TenantContext.require();
        ownedTask(tenantId, taskId);
        // state ∈ {triage, pool} 這個前提條件已經寫在引擎內同一句 UPDATE 的
        // WHERE 裡，這裡故意不自己先查詢再判斷要不要擋：那等於先查詢再更新，
        // 查詢與更新之間任務可能被別的請求改掉（讀到還在 pool、真正擋下之前
        // 已被認領），自己判斷反而會用到過期的狀態擋錯決定
        taskEngine.cancelByGuest(tenantId, taskId);
        return GuestTaskView.from(taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow());
    }

    public GuestTaskView rate(UUID taskId, GuestRatingRequest request) {
        long tenantId = TenantContext.require();
        if (request.rating() == null) {
            throw new UnprocessableEntityException("rating 必填（1–5 整數）");
        }
        ownedTask(tenantId, taskId);
        taskEngine.rate(tenantId, taskId, request.rating());
        return GuestTaskView.from(taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow());
    }

    /** 勾選單路徑：itemId→tenant_config request_menu 的預對應部門，直入 pool。 */
    private TaskCreateCommand menuCommand(long tenantId, CurrentActor actor, String itemId) {
        MenuItem item = configReader.requestMenu(tenantId).stream()
                .filter(candidate -> candidate.key().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new UnprocessableEntityException("選單裡沒有這個項目: " + itemId));
        return new TaskCreateCommand(
                tenantId, item.type(), TaskSource.GUEST, actor.roomId(), actor.reservationId(),
                item.dept(), TaskState.POOL, null, item.label(),
                noteJson(Map.of("menu_key", item.key())), null, null,
                workDateFor(item.type()), Actor.guest());
    }

    /** 自由文字路徑：規則引擎定去向，LLM 只翻譯與建議（留稽核、不改歸屬）。 */
    private TaskCreateCommand freeTextCommand(long tenantId, CurrentActor actor, String text, String lang) {
        Routing routing = ruleEngine.route(configReader.classificationRules(tenantId), text);

        Map<String, Object> noteFields = new LinkedHashMap<>();
        noteFields.put("text", text);
        if (lang != null && !lang.isBlank()) {
            noteFields.put("lang", lang.trim());
        }
        if (routing.ruleId() != null) {
            // 命中留痕（A10）：created 事件快照含 note，rule_id 由此可解釋
            // 「為什麼派給這個部門」；TaskEngine 的快照鍵集固定（touches 之外），
            // 留痕只能走 note 欄位
            noteFields.put("rule_id", routing.ruleId());
        }
        textAdvisor.advise(tenantId, text, lang).ifPresent(advice -> {
            // LLM 僅翻譯與建議，留稽核義務：建議進日誌與 note，但 routing 已定案、不回頭改
            log.info("[llm-advice] tenant={} 建議部門={} 翻譯={}（僅記錄，不改歸屬）",
                    tenantId, advice.suggestedDept(), advice.translatedText());
            if (advice.translatedText() != null) {
                noteFields.put("llm_translated", advice.translatedText());
            }
            if (advice.suggestedDept() != null) {
                noteFields.put("llm_suggested_dept", advice.suggestedDept());
            }
        });

        String title = text.length() <= TITLE_MAX_LENGTH ? text : text.substring(0, TITLE_MAX_LENGTH) + "…";
        return new TaskCreateCommand(
                tenantId, routing.type(), TaskSource.GUEST, actor.roomId(), actor.reservationId(),
                routing.dept(), routing.initialState(), null, title,
                noteJson(noteFields), null, null,
                workDateFor(routing.type()), Actor.guest());
    }

    /** cleaning 類建單必帶 work_date（一房一天僅一筆未完成打掃單的判定依據）；住客提清潔就是「今天來」。 */
    private static LocalDate workDateFor(String type) {
        return TaskType.CLEANING.equals(type) ? LocalDate.now() : null;
    }

    /**
     * 歸屬檢查：id 存在但掛在別的訂單（前住客、別房）→ 404 不是 403，
     * 貼紙在誰手上不可控，403 等於替人確認「這個單號存在」。
     */
    private void ownedTask(long tenantId, UUID taskId) {
        Task task = taskRepository.findByTenantIdAndId(tenantId, taskId)
                .orElseThrow(() -> new NotFoundException("需求不存在"));
        UUID sessionReservation = CurrentActor.require().reservationId();
        if (!sessionReservation.equals(task.getReservationId())) {
            throw new NotFoundException("需求不存在");
        }
    }

    private String noteJson(Map<String, Object> fields) {
        return objectMapper.writeValueAsString(fields);
    }
}
