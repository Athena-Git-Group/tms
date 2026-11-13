package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.DeskTaskFilter;
import com.athena.tms.dto.TriageDecisionRequest;
import com.athena.tms.entity.Task;
import com.athena.tms.service.TaskEngine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class DeskService {
    private static final String ASSIGN = "assign";
    private static final String ANSWER = "answer";
    /** 不會再變動的狀態：已結束的三種加上取消。 */
    private static final List<String> ENDED_STATES = Stream.concat(TaskState.FINISHED.stream(), Stream.of(TaskState.CANCELLED)).toList();
    private static final List<String> OPEN_STATES = TaskState.ALL.stream().filter(state -> !ENDED_STATES.contains(state)).toList();
    private final EntityManager entityManager;
    private final TaskEngine taskEngine;
    private final TaskRepository taskRepository;
    private final Clock clock;

    public DeskService(EntityManager entityManager, TaskEngine taskEngine, TaskRepository taskRepository, Clock clock) {
        this.entityManager = entityManager;
        this.taskEngine = taskEngine;
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    public List<Task> triageList() {
        long tenantId = TenantContext.require();
        return this.entityManager.createQuery("SELECT t FROM Task t\nWHERE t.tenantId = :tenantId AND t.state = :state\nORDER BY t.createdAt ASC\n", Task.class).setParameter("tenantId", (Object)tenantId).setParameter("state", (Object)"triage").getResultList();
    }

    /**
     * 櫃檯任務清單：本租戶工作日落在 filter 日期範圍內的任務，可再限定負責人與狀態。
     *
     * <p>「工作日」與員工首頁的今日口徑（{@code TaskExecutionService.belongsToToday}）同一把尺，
     * 櫃檯查今天看到的單才會和員工手機上的今日任務對得上。「今天」用同一個時鐘的時區。
     * 有 work_date 的單（打掃）看 work_date。沒有 work_date 的單（備品、其他、修繕）看狀態：
     * 還沒結束的就是今天手上的單，所以只有範圍含今天時才列出；已結束的看完成時間落在哪一天。
     * 取消的單沒有完成時間，改看最後異動時間，也就是取消的那一刻。
     * 不這樣處理的話，沒有工作日的取消單會永遠算成「今天」，清單又會無限累積。
     *
     * <p>每段條件都對應 V24 migration 的索引，改寫條件時要一起確認索引仍用得上。
     */
    public List<Task> storeTasks(DeskTaskFilter filter) {
        long tenantId = TenantContext.require();
        ZoneId zone = this.clock.getZone();
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantId);
        params.put("fromDate", filter.from());
        params.put("toDate", filter.to());
        params.put("endedStates", ENDED_STATES);
        params.put("rangeStart", filter.from().atStartOfDay(zone).toOffsetDateTime());
        params.put("rangeEnd", filter.to().plusDays(1L).atStartOfDay(zone).toOffsetDateTime());
        StringBuilder workDay = new StringBuilder("""
                (t.work_date BETWEEN :fromDate AND :toDate
                 OR (t.work_date IS NULL AND t.state IN (:endedStates)
                     AND COALESCE(t.done_at, t.updated_at) >= :rangeStart
                     AND COALESCE(t.done_at, t.updated_at) < :rangeEnd)""");
        if (filter.coversToday()) {
            workDay.append("\n OR (t.work_date IS NULL AND t.state IN (:openStates))");
            params.put("openStates", OPEN_STATES);
        }
        workDay.append(")");
        List<String> conditions = new ArrayList<>(List.of("t.tenant_id = :tenantId", workDay.toString()));
        filter.assigneeId().ifPresent(assigneeId -> {
            conditions.add("t.assignee_id = :assigneeId");
            params.put("assigneeId", assigneeId);
        });
        if (!filter.states().isEmpty()) {
            conditions.add("t.state IN (:states)");
            params.put("states", filter.states());
        }
        Query query = this.entityManager.createNativeQuery("SELECT t.* FROM tasks t\nWHERE " + String.join("\n  AND ", conditions) + "\nORDER BY t.created_at DESC\n", Task.class);
        params.forEach(query::setParameter);
        @SuppressWarnings("unchecked")
        List<Task> tasks = query.getResultList();
        return tasks;
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
