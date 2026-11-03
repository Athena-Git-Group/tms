package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.InspectionRepository;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.InspectionRequest;
import com.athena.tms.entity.Inspection;
import com.athena.tms.entity.Task;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 抽查與查房合格率（contracts 查房.yaml 的 createSpotCheck、getInspectionPassRate）。
 *
 * <p>抽查和一般查房（{@link InspectionService}）是兩條路：一般查房對還沒結案的
 * done 任務逐項判定全集，抽查對已免查結案的任務挑幾項複驗，不要求涵蓋項目全集。
 * 任一項不合格時整張單退回原負責人重做（狀態轉移見
 * {@link TaskEngine#failSpotCheck}），同時清空 SOP 勾記、把房間標回待整理，
 * 並推播原負責人；全部合格時任務維持原狀，只留一筆抽查紀錄。
 */
@Service
public class InspectionReviewService {

    private final TaskRepository taskRepository;
    private final InspectionRepository inspectionRepository;
    private final TaskEngine taskEngine;
    private final PushOutboxWriter pushOutboxWriter;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InspectionReviewService(
            TaskRepository taskRepository,
            InspectionRepository inspectionRepository,
            TaskEngine taskEngine,
            PushOutboxWriter pushOutboxWriter,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.inspectionRepository = inspectionRepository;
        this.taskEngine = taskEngine;
        this.pushOutboxWriter = pushOutboxWriter;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 合格率回應形狀（contracts PassRate）：分母為 0 時 rate 為 null。 */
    public record PassRate(int passed, int failed, Double rate) {}

    @Transactional
    public Inspection spotCheck(UUID taskId, List<InspectionRequest.Item> items) {
        long tenantId = TenantContext.require();
        CurrentActor inspector = CurrentActor.require();
        Task task = taskRepository
                .findByTenantIdAndId(tenantId, taskId)
                .orElseThrow(() -> new NotFoundException("查無此任務"));
        if (task.getRoomId() == null) {
            throw new UnprocessableEntityException("此任務未綁房間，無法抽查");
        }
        if (items == null || items.isEmpty()) {
            throw new UnprocessableEntityException("items 必填：至少抽查一個項目");
        }
        for (InspectionRequest.Item item : items) {
            if (item.key() == null || item.pass() == null) {
                throw new UnprocessableEntityException("抽查項目必須同時有 key 與 pass 判定");
            }
        }

        List<String> failedKeys = items.stream()
                .filter(item -> !item.pass())
                .map(InspectionRequest.Item::key)
                .toList();
        if (!failedKeys.isEmpty()) {
            // 不合格項以項目 key 直接串接呈現：抽查請求只帶 key，沒有租戶 SOP 項目的顯示名稱可查。
            String failedKeysText = String.join("、", failedKeys);
            Actor actor = Actor.staff(inspector.role(), inspector.staffId());
            int seq = taskEngine.failSpotCheck(tenantId, taskId, failedKeysText, actor);
            // 重做要從乾淨狀態開始：上一輪的勾記若留著，重做時清單會呈現已完成。
            jdbcTemplate.update(
                    "DELETE FROM task_sop_checks WHERE tenant_id = ? AND task_id = ?", tenantId, taskId);
            String roomNo = markRoomDirty(tenantId, task.getRoomId());
            notifyAssignee(tenantId, task, seq, roomNo, failedKeysText);
        }
        return recordSpotCheck(tenantId, taskId, inspector.staffId(), failedKeys.isEmpty(), items);
    }

    /** 合格率只計通過與退回重做：passed 是分子，passed＋failed 是分母，免查（waived）不計入。 */
    public PassRate passRate(LocalDate date) {
        long tenantId = TenantContext.require();
        LocalDate workDate = date != null ? date : LocalDate.ofInstant(clock.instant(), clock.getZone());
        int passed = countInspection(tenantId, workDate, "passed");
        int failed = countInspection(tenantId, workDate, "failed");
        int total = passed + failed;
        return new PassRate(passed, failed, total == 0 ? null : (double) passed / total);
    }

    private int countInspection(long tenantId, LocalDate workDate, String inspection) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM tasks
                 WHERE tenant_id = ? AND type = 'cleaning' AND work_date = ? AND inspection = ?
                """,
                Integer.class, tenantId, workDate, inspection);
        return count == null ? 0 : count;
    }

    /** 把任務綁定房間的 hk_state 標回 dirty（退回重做代表這間房還不能算整理完），回傳房號。 */
    private String markRoomDirty(long tenantId, UUID roomId) {
        return jdbcTemplate.queryForObject(
                "UPDATE rooms SET hk_state = 'dirty', updated_at = now() WHERE tenant_id = ? AND id = ? RETURNING room_no",
                String.class, tenantId, roomId);
    }

    /**
     * 推播原負責人任務被退回。冪等鍵綁「任務＋這次退回的事件序號」，請求重試或
     * 掃描重放時同一次退回不會對負責人重複推播。
     */
    private void notifyAssignee(long tenantId, Task task, int seq, String roomNo, String failedKeysText) {
        if (task.getAssigneeId() == null) {
            return;
        }
        ObjectNode payload = PushOutboxWriter.payload(
                objectMapper, task.getAssigneeId(), "inspection_failed",
                roomNo + " 查房退回 · " + failedKeysText + "不合格");
        PushOutboxWriter.targetTask(payload, task.getId());
        pushOutboxWriter.enqueue(
                tenantId,
                "task:" + task.getId(),
                payload,
                "spot_check_failed:" + task.getId() + ":seq:" + seq);
    }

    /** 抽查一次留一筆查房紀錄（inspections 是 history，退回重查會有多列）。 */
    private Inspection recordSpotCheck(
            long tenantId, UUID taskId, UUID inspectorId, boolean allPass, List<InspectionRequest.Item> items) {
        ArrayNode itemsJson = objectMapper.createArrayNode();
        for (InspectionRequest.Item item : items) {
            ObjectNode node = itemsJson.addObject();
            node.put("key", item.key());
            node.put("pass", item.pass());
        }
        Inspection inspection = new Inspection();
        inspection.setId(UuidV7.generate());
        inspection.setTenantId(tenantId);
        inspection.setTaskId(taskId);
        inspection.setInspectorId(inspectorId);
        inspection.setResult(allPass ? "passed" : "failed");
        inspection.setItems(itemsJson.toString());
        inspection.setCreatedAt(OffsetDateTime.now(clock));
        return inspectionRepository.save(inspection);
    }
}
