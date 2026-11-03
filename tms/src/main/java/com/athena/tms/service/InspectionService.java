package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.InspectionRepository;
import com.athena.tms.dao.LeaveRepository;
import com.athena.tms.dao.RoomRepository;
import com.athena.tms.dao.SopItemRepository;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dao.TaskSopCheckRepository;
import com.athena.tms.dao.TenantConfigRepository;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.InspectionRequest;
import com.athena.tms.dto.InspectionResponse;
import com.athena.tms.dto.ShiftTimes;
import com.athena.tms.entity.Inspection;
import com.athena.tms.entity.Room;
import com.athena.tms.entity.SopItem;
import com.athena.tms.entity.Task;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class InspectionService {
    private final TaskRepository taskRepository;
    private final SopItemRepository sopItemRepository;
    private final InspectionRepository inspectionRepository;
    private final TaskSopCheckRepository taskSopCheckRepository;
    private final RoomRepository roomRepository;
    private final LeaveRepository leaveRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final TaskEngine taskEngine;
    private final OutboxWriter outboxWriter;
    private final PushOutboxWriter pushOutboxWriter;
    private final ObjectMapper objectMapper;

    public InspectionService(TaskRepository taskRepository, SopItemRepository sopItemRepository, InspectionRepository inspectionRepository, TaskSopCheckRepository taskSopCheckRepository, RoomRepository roomRepository, LeaveRepository leaveRepository, TenantConfigRepository tenantConfigRepository, TaskEngine taskEngine, OutboxWriter outboxWriter, PushOutboxWriter pushOutboxWriter, ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.sopItemRepository = sopItemRepository;
        this.inspectionRepository = inspectionRepository;
        this.taskSopCheckRepository = taskSopCheckRepository;
        this.roomRepository = roomRepository;
        this.leaveRepository = leaveRepository;
        this.tenantConfigRepository = tenantConfigRepository;
        this.taskEngine = taskEngine;
        this.outboxWriter = outboxWriter;
        this.pushOutboxWriter = pushOutboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InspectionResponse inspect(UUID taskId, InspectionRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor inspector = CurrentActor.require();
        Task task = this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow(() -> new NotFoundException("查無此任務"));
        if (inspector.staffId() != null && inspector.staffId().equals(task.getAssigneeId())) {
            throw new ForbiddenException("查房者不得為原清房者（E-07）");
        }
        if (!"cleaning".equals(task.getType())) {
            throw new UnprocessableEntityException("查房只適用打掃任務，此單種類為 " + task.getType());
        }
        if (task.getRoomId() == null) {
            throw new UnprocessableEntityException("此任務未綁房間，無法查房");
        }
        List<SopItem> requiredItems = this.sopItemRepository.findByTenantIdAndAppliesToAndActiveTrueOrderBySortOrderAsc(tenantId, "inspection");
        Map<String, Boolean> judgements = this.validateCoverage(request, requiredItems);
        Room room = this.roomRepository.findByTenantIdAndId(tenantId, task.getRoomId()).orElseThrow(() -> new NotFoundException("查無任務綁定的房間"));
        Actor actor = Actor.staff(inspector.role(), inspector.staffId());
        boolean allPass = judgements.values().stream().allMatch(Boolean::booleanValue);
        if (allPass) {
            int passSeq = this.taskEngine.passInspection(tenantId, taskId, actor);
            this.updateHkState(room, "inspected");
            this.recordInspection(tenantId, taskId, inspector.staffId(), "passed", judgements);
            this.notifyAssignee(tenantId, task, passSeq, "inspection_passed", room.getRoomNo() + " 查房通過");
            // 查房通過＝合格結案：回寫 PMS 房況（回寫開關關閉或手動匯入模式時由該方法自行略過），
            // 任務事件因此在 inspection_passed 之後多一筆 pms_writeback。
            this.taskEngine.writebackRoomStatusIfEnabled(tenantId, taskId, actor);
            return new InspectionResponse(taskId, "passed", "inspected", task.getRedoCount(), false);
        }
        String failedLabels = String.join((CharSequence)"、", requiredItems.stream().filter(item -> (Boolean)judgements.get(item.getItemKey()) == false).map(SopItem::getLabel).toList());
        int failSeq = this.taskEngine.failInspection(tenantId, taskId, failedLabels, actor);
        this.notifyAssignee(tenantId, task, failSeq, "inspection_failed",
                room.getRoomNo() + " 查房退回 · " + failedLabels + "不合格");
        this.taskSopCheckRepository.deleteByTenantIdAndTaskId(tenantId, taskId);
        this.updateHkState(room, "dirty");
        this.recordInspection(tenantId, taskId, inspector.staffId(), "failed", judgements);
        boolean returnedToPool = false;
        UUID cleanerId = task.getAssigneeId();
        if (cleanerId != null && this.cleanerUnavailable(tenantId, cleanerId)) {
            int returnSeq = this.taskEngine.returnToPool(tenantId, taskId, "查房退回時原清房者已下班或請假，自動退回待接池", actor);
            this.outboxWriter.enqueuePush(tenantId, taskId, "task:" + String.valueOf(taskId) + ":returned_to_pool:seq:" + returnSeq, this.returnedToPoolPushPayload(room, task, failedLabels));
            returnedToPool = true;
        }
        return new InspectionResponse(taskId, "failed", returnedToPool ? "pool" : "accepted", task.getRedoCount() + 1, returnedToPool);
    }

    private Map<String, Boolean> validateCoverage(InspectionRequest request, List<SopItem> requiredItems) {
        if (requiredItems.isEmpty()) {
            // 租戶沒有設定查房項目：沒有可逐項判定的清單，「涵蓋全集」空集合成立，
            // 整體判定視為全部通過（不強迫每個租戶都得先建查房項目才能結案）。
            return new LinkedHashMap<String, Boolean>();
        }
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new UnprocessableEntityException("items 必填：須涵蓋租戶查房項目全集");
        }
        LinkedHashMap<String, Boolean> judgements = new LinkedHashMap<String, Boolean>();
        for (SopItem sopItem : requiredItems) {
            judgements.put(sopItem.getItemKey(), null);
        }
        for (InspectionRequest.Item item : request.items()) {
            if (item.key() == null || !judgements.containsKey(item.key())) {
                throw new UnprocessableEntityException("未知的查房項目: " + item.key());
            }
            if (judgements.get(item.key()) != null) {
                throw new UnprocessableEntityException("查房項目重複判定: " + item.key());
            }
            if (item.pass() == null) {
                throw new UnprocessableEntityException("查房項目 " + item.key() + " 缺 pass 判定");
            }
            judgements.put(item.key(), item.pass());
        }
        List<String> missing = judgements.entrySet().stream().filter(entry -> entry.getValue() == null).map(Map.Entry::getKey).toList();
        if (!missing.isEmpty()) {
            throw new UnprocessableEntityException("items 須涵蓋租戶查房項目全集，缺: " + String.join((CharSequence)", ", missing));
        }
        return judgements;
    }

    private boolean cleanerUnavailable(long tenantId, UUID cleanerId) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean onLeave = this.leaveRepository.existsByTenantIdAndStaffIdAndFromAtLessThanEqualAndToAtGreaterThan(tenantId, cleanerId, now, now);
        if (onLeave) {
            return true;
        }
        return !this.isWithinShift(tenantId, now.toLocalTime());
    }

    private boolean isWithinShift(long tenantId, LocalTime at) {
        return this.tenantConfigRepository.findByTenantIdAndKey(tenantId, "shift_times").map(config -> {
            try {
                return ShiftTimes.coversTime(this.objectMapper.readTree(config.getValue()), at);
            }
            catch (JacksonException e) {
                return true;
            }
        }).orElse(true);
    }

    private void recordInspection(long tenantId, UUID taskId, UUID inspectorId, String result, Map<String, Boolean> judgements) {
        ArrayNode items = this.objectMapper.createArrayNode();
        judgements.forEach((key, pass) -> {
            ObjectNode item = items.addObject();
            item.put("key", key);
            item.put("pass", pass);
        });
        Inspection inspection = new Inspection();
        inspection.setId(UuidV7.generate());
        inspection.setTenantId(tenantId);
        inspection.setTaskId(taskId);
        inspection.setInspectorId(inspectorId);
        inspection.setResult(result);
        inspection.setItems(items.toString());
        inspection.setCreatedAt(OffsetDateTime.now());
        this.inspectionRepository.save(inspection);
    }

    /**
     * 查房結果要讓負責人即時知道，他才知道該去接下一間還是回頭重做。
     * 推播寫進 outbox，冪等鍵帶這次查房的事件序號，請求重試時同一次查房
     * 不會對負責人插入第二筆。沒有負責人的單沒有收件對象，直接略過。
     */
    private void notifyAssignee(long tenantId, Task task, int seq, String event, String title) {
        if (task.getAssigneeId() == null) {
            return;
        }
        ObjectNode payload = PushOutboxWriter.payload(this.objectMapper, task.getAssigneeId(), event, title);
        PushOutboxWriter.targetTask(payload, task.getId());
        this.pushOutboxWriter.enqueue(tenantId, "task:" + task.getId(), payload,
                event + ":" + task.getId() + ":seq:" + seq + ":" + task.getAssigneeId());
    }

    private void updateHkState(Room room, String hkState) {
        room.setHkState(hkState);
        room.setUpdatedAt(OffsetDateTime.now());
        this.roomRepository.save(room);
    }

    private String returnedToPoolPushPayload(Room room, Task task, String failedLabels) {
        ObjectNode payload = this.objectMapper.createObjectNode();
        payload.put("type", "returned_to_pool");
        payload.put("room_no", room.getRoomNo());
        if (room.getFloor() != null) {
            payload.put("floor", room.getFloor());
        }
        payload.put("dept", task.getDept());
        payload.put("hint", "prefer_same_floor");
        payload.put("failed_items", failedLabels);
        return payload.toString();
    }
}
