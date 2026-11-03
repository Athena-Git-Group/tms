/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  tools.jackson.core.JacksonException
 *  tools.jackson.databind.ObjectMapper
 *  tools.jackson.databind.node.ArrayNode
 *  tools.jackson.databind.node.ObjectNode
 */
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
import com.athena.tms.service.OutboxWriter;
import com.athena.tms.service.TaskEngine;
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
    private final ObjectMapper objectMapper;

    public InspectionService(TaskRepository taskRepository, SopItemRepository sopItemRepository, InspectionRepository inspectionRepository, TaskSopCheckRepository taskSopCheckRepository, RoomRepository roomRepository, LeaveRepository leaveRepository, TenantConfigRepository tenantConfigRepository, TaskEngine taskEngine, OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.sopItemRepository = sopItemRepository;
        this.inspectionRepository = inspectionRepository;
        this.taskSopCheckRepository = taskSopCheckRepository;
        this.roomRepository = roomRepository;
        this.leaveRepository = leaveRepository;
        this.tenantConfigRepository = tenantConfigRepository;
        this.taskEngine = taskEngine;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InspectionResponse inspect(UUID taskId, InspectionRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor inspector = CurrentActor.require();
        Task task = this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow(() -> new NotFoundException("\u67e5\u7121\u6b64\u4efb\u52d9"));
        if (inspector.staffId() != null && inspector.staffId().equals(task.getAssigneeId())) {
            throw new ForbiddenException("\u67e5\u623f\u8005\u4e0d\u5f97\u70ba\u539f\u6e05\u623f\u8005\uff08E-07\uff09");
        }
        if (!"cleaning".equals(task.getType())) {
            throw new UnprocessableEntityException("\u67e5\u623f\u53ea\u9069\u7528\u6253\u6383\u4efb\u52d9\uff0c\u6b64\u55ae\u7a2e\u985e\u70ba " + task.getType());
        }
        if (task.getRoomId() == null) {
            throw new UnprocessableEntityException("\u6b64\u4efb\u52d9\u672a\u7d81\u623f\u9593\uff0c\u7121\u6cd5\u67e5\u623f");
        }
        List<SopItem> requiredItems = this.sopItemRepository.findByTenantIdAndAppliesToAndActiveTrueOrderBySortOrderAsc(tenantId, "inspection");
        Map<String, Boolean> judgements = this.validateCoverage(request, requiredItems);
        Room room = this.roomRepository.findByTenantIdAndId(tenantId, task.getRoomId()).orElseThrow(() -> new NotFoundException("\u67e5\u7121\u4efb\u52d9\u7d81\u5b9a\u7684\u623f\u9593"));
        Actor actor = Actor.staff(inspector.role(), inspector.staffId());
        boolean allPass = judgements.values().stream().allMatch(Boolean::booleanValue);
        if (allPass) {
            this.taskEngine.passInspection(tenantId, taskId, actor);
            this.updateHkState(room, "inspected");
            this.recordInspection(tenantId, taskId, inspector.staffId(), "passed", judgements);
            return new InspectionResponse(taskId, "passed", "inspected", task.getRedoCount(), false);
        }
        String failedLabels = String.join((CharSequence)"\u3001", requiredItems.stream().filter(item -> (Boolean)judgements.get(item.getItemKey()) == false).map(SopItem::getLabel).toList());
        this.taskEngine.failInspection(tenantId, taskId, failedLabels, actor);
        this.taskSopCheckRepository.deleteByTenantIdAndTaskId(tenantId, taskId);
        this.updateHkState(room, "dirty");
        this.recordInspection(tenantId, taskId, inspector.staffId(), "failed", judgements);
        boolean returnedToPool = false;
        UUID cleanerId = task.getAssigneeId();
        if (cleanerId != null && this.cleanerUnavailable(tenantId, cleanerId)) {
            int returnSeq = this.taskEngine.returnToPool(tenantId, taskId, "\u67e5\u623f\u9000\u56de\u6642\u539f\u6e05\u623f\u8005\u5df2\u4e0b\u73ed\u6216\u8acb\u5047\uff0c\u81ea\u52d5\u9000\u56de\u5f85\u63a5\u6c60", actor);
            this.outboxWriter.enqueuePush(tenantId, taskId, "task:" + String.valueOf(taskId) + ":returned_to_pool:seq:" + returnSeq, this.returnedToPoolPushPayload(room, task, failedLabels));
            returnedToPool = true;
        }
        return new InspectionResponse(taskId, "failed", returnedToPool ? "pool" : "accepted", task.getRedoCount() + 1, returnedToPool);
    }

    private Map<String, Boolean> validateCoverage(InspectionRequest request, List<SopItem> requiredItems) {
        if (requiredItems.isEmpty()) {
            throw new UnprocessableEntityException("\u79df\u6236\u5c1a\u672a\u8a2d\u5b9a\u67e5\u623f\u9805\u76ee\uff0c\u7121\u6cd5\u67e5\u623f");
        }
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new UnprocessableEntityException("items \u5fc5\u586b\uff1a\u9808\u6db5\u84cb\u79df\u6236\u67e5\u623f\u9805\u76ee\u5168\u96c6");
        }
        LinkedHashMap<String, Boolean> judgements = new LinkedHashMap<String, Boolean>();
        for (SopItem sopItem : requiredItems) {
            judgements.put(sopItem.getItemKey(), null);
        }
        for (InspectionRequest.Item item : request.items()) {
            if (item.key() == null || !judgements.containsKey(item.key())) {
                throw new UnprocessableEntityException("\u672a\u77e5\u7684\u67e5\u623f\u9805\u76ee: " + item.key());
            }
            if (judgements.get(item.key()) != null) {
                throw new UnprocessableEntityException("\u67e5\u623f\u9805\u76ee\u91cd\u8907\u5224\u5b9a: " + item.key());
            }
            if (item.pass() == null) {
                throw new UnprocessableEntityException("\u67e5\u623f\u9805\u76ee " + item.key() + " \u7f3a pass \u5224\u5b9a");
            }
            judgements.put(item.key(), item.pass());
        }
        List<String> missing = judgements.entrySet().stream().filter(entry -> entry.getValue() == null).map(Map.Entry::getKey).toList();
        if (!missing.isEmpty()) {
            throw new UnprocessableEntityException("items \u9808\u6db5\u84cb\u79df\u6236\u67e5\u623f\u9805\u76ee\u5168\u96c6\uff0c\u7f3a: " + String.join((CharSequence)", ", missing));
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
