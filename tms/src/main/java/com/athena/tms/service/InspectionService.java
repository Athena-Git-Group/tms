package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
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

    /**
     * 一次查房判定的結果：通過或退回、單的去向，以及結果畫面要用的房號、原清房者與不通過項目。
     * {@code destination} 是 closed（結案）、cleaner（退回原清房者名下）或 pool（原清房者已下班或請假，
     * 回房務待認領）三者之一；{@code redoCount} 是判定後任務的重做次數。
     */
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

    @Transactional
    public Judged inspect(UUID taskId, InspectionRequest request) {
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
        ItemCoverage coverage = this.checkCoverage(request, requiredItems);
        if (!coverage.matches()) {
            // 拒絕發生在任何狀態轉移之前：單不變、不寫查房紀錄、不推播。
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
            // 查房通過＝合格結案：回寫 PMS 房況（回寫開關關閉或手動匯入模式時由該方法自行略過），
            // 任務事件因此在 inspection_passed 之後多一筆 pms_writeback。
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

    /**
     * 這次送出的項目與飯店現行啟用項目的比對結果。{@code judgements} 依飯店排序列出現行項目的判定，
     * 沒送到的項目值為 null；{@code missing} 是少送的鍵，{@code unexpected} 是多送（飯店已沒有）的鍵。
     */
    private record ItemCoverage(
            Map<String, Boolean> judgements,
            List<String> missing,
            List<String> unexpected,
            List<SopItem> currentItems) {

        boolean matches() {
            return missing.isEmpty() && unexpected.isEmpty();
        }

        /** 對應 contracts Error 的 inspection_items_mismatch context，currentItems 每筆帶 key、label、sortOrder。 */
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

    /**
     * 比對這次送出的項目集合與飯店現行啟用的查房項目。少項與多項都算不符，由呼叫端拒絕整次判定；
     * 若只拒缺項而默默忽略多送的項目，主管對已停用項目判的「不通過」會被吞掉，單卻照樣結案。
     * 飯店沒有任何啟用項目時，空的判定清單剛好相符，視為全部通過。同一項重複送或缺判定值
     * 是請求本身寫錯，直接回 422 validation_failed。
     */
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
