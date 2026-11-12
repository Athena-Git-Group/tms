package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.ManagerKind;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.codes.RolePermissions;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.InspectionRepository;
import com.athena.tms.dao.RoomRepository;
import com.athena.tms.dao.SopItemRepository;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dao.TaskSopCheckRepository;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.InspectionRequest;
import com.athena.tms.dto.InspectionOutcome;
import com.athena.tms.entity.Inspection;
import com.athena.tms.entity.Room;
import com.athena.tms.entity.SopItem;
import com.athena.tms.entity.StaffAccount;
import com.athena.tms.entity.Task;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final StaffAvailability staffAvailability;
    private final TaskEngine taskEngine;
    private final OutboxWriter outboxWriter;
    private final PushOutboxWriter pushOutboxWriter;
    private final ObjectMapper objectMapper;
    private final StaffAccountRepository staffAccountRepository;

    public InspectionService(TaskRepository taskRepository, SopItemRepository sopItemRepository, InspectionRepository inspectionRepository, TaskSopCheckRepository taskSopCheckRepository, RoomRepository roomRepository, StaffAvailability staffAvailability, TaskEngine taskEngine, OutboxWriter outboxWriter, PushOutboxWriter pushOutboxWriter, ObjectMapper objectMapper, StaffAccountRepository staffAccountRepository) {
        this.taskRepository = taskRepository;
        this.sopItemRepository = sopItemRepository;
        this.inspectionRepository = inspectionRepository;
        this.taskSopCheckRepository = taskSopCheckRepository;
        this.roomRepository = roomRepository;
        this.staffAvailability = staffAvailability;
        this.taskEngine = taskEngine;
        this.outboxWriter = outboxWriter;
        this.pushOutboxWriter = pushOutboxWriter;
        this.objectMapper = objectMapper;
        this.staffAccountRepository = staffAccountRepository;
    }

    public record Judged(
            UUID inspectionId,
            UUID taskId,
            String roomNo,
            boolean passed,
            String destination,
            String cleanerName,
            List<InspectionOutcome.FailedItem> failedItems,
            OffsetDateTime createdAt,
            int redoCount) {}

    private static void requireMayInspect(CurrentActor inspector, Task task) {
        if (!RolePermissions.canInspectSubmit(inspector.role())) {
            throw new ForbiddenException("此角色沒有查房判定（inspect_submit）權限");
        }
        if (ManagerKind.of(inspector).filter(kind -> !kind.inspects()).isPresent()) {
            throw new ForbiddenException("你沒有查房的權限");
        }
        if (inspector.staffId() != null && inspector.staffId().equals(task.getAssigneeId())) {
            throw new ForbiddenException("查房者不得為原清房者");
        }
    }

    @Transactional
    public Judged inspect(UUID taskId, InspectionRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor inspector = CurrentActor.require();
        Task task = this.taskRepository.findByTenantIdAndId(tenantId, taskId).orElseThrow(() -> new NotFoundException("查無此任務"));
        requireMayInspect(inspector, task);
        if (!"cleaning".equals(task.getType())) {
            throw new UnprocessableEntityException("查房只適用打掃任務，此單種類為 " + task.getType());
        }
        if (task.getRoomId() == null) {
            throw new UnprocessableEntityException("此任務未綁房間，無法查房");
        }
        List<SopItem> requiredItems = this.sopItemRepository.findByTenantIdAndAppliesToAndActiveTrueOrderBySortOrderAsc(tenantId, "inspection");
        ItemCoverage coverage = this.checkCoverage(request, requiredItems);
        if (!coverage.matches()) {
            throw new UnprocessableEntityException("inspection_items_mismatch", "查房項目與飯店現行項目不符", coverage.mismatchContext());
        }
        Map<String, Boolean> judgements = coverage.judgements();
        Room room = this.roomRepository.findByTenantIdAndId(tenantId, task.getRoomId()).orElseThrow(() -> new NotFoundException("查無任務綁定的房間"));
        Actor actor = Actor.staff(inspector.role(), inspector.staffId());
        UUID cleanerId = task.getAssigneeId();
        String cleanerName = cleanerId == null ? null
                : this.staffAccountRepository.findByTenantIdAndId(tenantId, cleanerId).map(StaffAccount::getName).orElse(null);
        boolean allPass = judgements.values().stream().allMatch(Boolean::booleanValue);
        if (allPass) {
            int passSeq = this.taskEngine.passInspection(tenantId, taskId, actor);
            this.updateHkState(room, "inspected");
            Inspection inspection = this.recordInspection(tenantId, taskId, inspector.staffId(), "passed", judgements);
            this.notifyAssignee(tenantId, task, passSeq, "inspection_passed", room.getRoomNo() + " 查房通過");
            this.taskEngine.writebackRoomStatusIfEnabled(tenantId, taskId, actor);
            return new Judged(inspection.getId(), taskId, room.getRoomNo(), true, "closed", cleanerName,
                    List.of(), inspection.getCreatedAt(), task.getRedoCount());
        }
        List<InspectionOutcome.FailedItem> failedItems = requiredItems.stream()
                .filter(item -> Boolean.FALSE.equals(judgements.get(item.getItemKey())))
                .map(item -> new InspectionOutcome.FailedItem(item.getItemKey(), item.getLabel()))
                .toList();
        String failedLabels = String.join("、", failedItems.stream().map(InspectionOutcome.FailedItem::label).toList());
        int failSeq = this.taskEngine.failInspection(tenantId, taskId, failedLabels, actor);
        this.notifyAssignee(tenantId, task, failSeq, "inspection_failed",
                room.getRoomNo() + " 查房退回 · " + failedLabels + "不合格");
        this.taskSopCheckRepository.deleteByTenantIdAndTaskId(tenantId, taskId);
        this.updateHkState(room, "dirty");
        Inspection inspection = this.recordInspection(tenantId, taskId, inspector.staffId(), "failed", judgements);
        String destination = "cleaner";
        if (cleanerId != null && this.staffAvailability.unavailable(tenantId, cleanerId)) {
            int returnSeq = this.taskEngine.returnToPool(tenantId, taskId, "查房退回時原清房者已下班或請假，自動退回待接池", actor);
            this.outboxWriter.enqueuePush(tenantId, taskId, "task:" + String.valueOf(taskId) + ":returned_to_pool:seq:" + returnSeq, this.returnedToPoolPushPayload(room, task, failedLabels));
            destination = "pool";
        }
        return new Judged(inspection.getId(), taskId, room.getRoomNo(), false, destination, cleanerName,
                failedItems, inspection.getCreatedAt(), task.getRedoCount() + 1);
    }

    private record ItemCoverage(
            Map<String, Boolean> judgements,
            List<String> missing,
            List<String> unexpected,
            List<SopItem> currentItems) {

        boolean matches() {
            return missing.isEmpty() && unexpected.isEmpty();
        }

        Map<String, Object> mismatchContext() {
            List<Map<String, Object>> current = new ArrayList<Map<String, Object>>();
            for (SopItem item : currentItems) {
                Map<String, Object> entry = new LinkedHashMap<String, Object>();
                entry.put("key", item.getItemKey());
                entry.put("label", item.getLabel());
                entry.put("sortOrder", item.getSortOrder());
                current.add(entry);
            }
            Map<String, Object> context = new LinkedHashMap<String, Object>();
            context.put("missing", missing);
            context.put("unexpected", unexpected);
            context.put("currentItems", current);
            return context;
        }
    }

    private ItemCoverage checkCoverage(InspectionRequest request, List<SopItem> requiredItems) {
        List<InspectionRequest.Item> sent = request == null || request.items() == null ? List.of() : request.items();
        LinkedHashMap<String, Boolean> judgements = new LinkedHashMap<String, Boolean>();
        for (SopItem sopItem : requiredItems) {
            judgements.put(sopItem.getItemKey(), null);
        }
        List<String> unexpected = new ArrayList<String>();
        for (InspectionRequest.Item item : sent) {
            if (item.key() == null || !judgements.containsKey(item.key())) {
                unexpected.add(item.key());
                continue;
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
        return new ItemCoverage(judgements, missing, unexpected, requiredItems);
    }

    private Inspection recordInspection(long tenantId, UUID taskId, UUID inspectorId, String result, Map<String, Boolean> judgements) {
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
        return this.inspectionRepository.save(inspection);
    }

    private void notifyAssignee(long tenantId, Task task, int seq, String event, String title) {
        if (task.getAssigneeId() == null) {
            return;
        }
        ObjectNode payload = PushOutboxWriter.payload(this.objectMapper, task.getAssigneeId(), event, title);
        PushOutboxWriter.targetOwnedTask(payload, task.getType(), task.getId());
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
