package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.codes.Dept;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.entity.Task;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dto.Actor;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import com.athena.tms.dto.TriageDecisionRequest;

/**
 * 櫃檯判讀。轉移不自己做、一律呼叫 TaskEngine——合法性只在引擎
 * 一處定義，這裡自己 UPDATE 就是第二套狀態機的開端。本類剩下的職責，
 * 是三件引擎管不到的事：用租戶範圍查詢先判 404（引擎的條件式更新前提
 * 不符時一律回 409、改動 0 列，分不出是「單不存在」還是「狀態不符」，
 * 404 必須在呼叫引擎之前自己判斷）、三選一的請求值域驗證（dept 只收
 * 工作部門，防止呼叫方塞進引擎不認的值），以及唯讀清單（不經引擎、
 * 純讀取不影響狀態）。
 *
 * <p>清單查詢用 EntityManager 下 JPQL 而非在 TaskRepository 加方法：
 * 這兩個讀法只有櫃檯用，不值得進共用的 repository 介面，
 * 「本店全部 triage」「本店所有任務」就地宣告，一樣帶 tenant 條件
 * （第一道牆），RLS 第二道牆照常生效。
 */
@Service
public class DeskService {

    private static final String ASSIGN = "assign";
    private static final String ANSWER = "answer";

    private final EntityManager entityManager;
    private final TaskEngine taskEngine;
    private final TaskRepository taskRepository;

    public DeskService(EntityManager entityManager,
                       TaskEngine taskEngine,
                       TaskRepository taskRepository) {
        this.entityManager = entityManager;
        this.taskEngine = taskEngine;
        this.taskRepository = taskRepository;
    }

    /** 本店全部待判讀（triage）任務，先進先出（等最久的先被判讀）。 */
    public List<Task> triageList() {
        long tenantId = TenantContext.require();
        return entityManager.createQuery("""
                        SELECT t FROM Task t
                        WHERE t.tenantId = :tenantId AND t.state = :state
                        ORDER BY t.createdAt ASC
                        """, Task.class)
                .setParameter("tenantId", tenantId)
                .setParameter("state", TaskState.TRIAGE)
                .getResultList();
    }

    /** 本店所有任務（防重複建單的查詢面），新單在前，因為查重通常只看最近。 */
    public List<Task> storeTasks() {
        long tenantId = TenantContext.require();
        return entityManager.createQuery("""
                        SELECT t FROM Task t
                        WHERE t.tenantId = :tenantId
                        ORDER BY t.createdAt DESC
                        """, Task.class)
                .setParameter("tenantId", tenantId)
                .getResultList();
    }

    /**
     * 櫃檯判讀的三選一：指派部門、當場回答、取消。assign 的 dept 值域與任務
     * 同一套、但只收工作部門（housekeeping|engineering）。frontdesk 不是
     * assign 的合法去向，「留在櫃檯處理」的出路是 answer。非 triage 狀態由
     * 引擎的條件式更新擋成 409。
     *
     * <p>answer 的回覆由 TaskEngine.answer 同句寫進 tasks.note（spec 要求
     * 「回覆存任務 note、住客端可見」），並照例進 answered 事件 payload。
     */
    public Task decide(UUID taskId, TriageDecisionRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        requireTask(tenantId, taskId);
        Actor deskActor = Actor.staff(actor.role(), actor.staffId());

        if (ASSIGN.equals(request.action())) {
            String dept = request.dept();
            if (!Dept.HOUSEKEEPING.equals(dept) && !Dept.ENGINEERING.equals(dept)) {
                throw new UnprocessableEntityException(
                        "assign 的 dept 值域僅 housekeeping|engineering（FR-021），收到: " + dept);
            }
            taskEngine.assignFromTriage(tenantId, taskId, dept, deskActor);
        } else if (ANSWER.equals(request.action())) {
            if (request.note() == null || request.note().isBlank()) {
                throw new UnprocessableEntityException("answer 必附 note（回覆內容，住客端可見）");
            }
            taskEngine.answer(tenantId, taskId, request.note(), deskActor);
        } else {
            throw new UnprocessableEntityException(
                    "action 僅 assign|answer（三選一的第三選＝answer 當場回答），收到: " + request.action());
        }
        return requireTask(tenantId, taskId);
    }

    /** 跨租戶與不存在一律 404 not_found。引擎的條件式更新改動 0 列只會給 409，404 得在它之前判。 */
    private Task requireTask(long tenantId, UUID taskId) {
        return taskRepository.findByTenantIdAndId(tenantId, taskId)
                .orElseThrow(() -> new NotFoundException("查無任務: " + taskId));
    }
}
