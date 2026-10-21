package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.codes.InspectionResult;
import com.athena.tms.common.codes.RoomHkState;
import com.athena.tms.common.codes.SopAppliesTo;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.common.codes.TaskType;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.entity.Inspection;
import com.athena.tms.entity.Room;
import com.athena.tms.entity.SopItem;
import com.athena.tms.entity.Task;
import com.athena.tms.dao.InspectionRepository;
import com.athena.tms.dao.LeaveRepository;
import com.athena.tms.dao.RoomRepository;
import com.athena.tms.dao.SopItemRepository;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dao.TaskSopCheckRepository;
import com.athena.tms.dao.TenantConfigRepository;
import com.athena.tms.dto.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.athena.tms.dto.InspectionRequest;
import com.athena.tms.dto.InspectionResponse;
import com.athena.tms.dto.ShiftTimes;

/**
 * 主管查房。狀態轉移本身一律經 TaskEngine 這個唯一入口；
 * 本類負責引擎管不到的查房周邊——items 鍵集驗證、自清自查
 * 禁令、inspections 記錄、rooms.hk_state 回寫、退回連動與退池推播落匣。
 * 整組動作鎖在一筆交易裡（引擎方法 REQUIRED 傳播加入本類的交易），因為
 * 任何一半成立都是災難：任務 inspected 了但 hk_state 沒回寫＝房看起來
 * 還不能賣；退了單卻沒清勾記＝清房者照舊勾記看起來「做完了」。
 *
 * <p>rooms.hk_state 的回寫寫在這裡而非引擎內：hk_state 是房的欄位不是任務
 * 狀態，引擎用來擋狀態轉移的條件式更新（前提不符就一列也不改）管不到它；
 * 但它只隨查房轉移同交易變動（erm.dbml 的「僅任務引擎路徑可改」不變條件）
 * ——除了這條路徑，程式裡沒有第二個寫點。
 */
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

    public InspectionService(TaskRepository taskRepository,
                             SopItemRepository sopItemRepository,
                             InspectionRepository inspectionRepository,
                             TaskSopCheckRepository taskSopCheckRepository,
                             RoomRepository roomRepository,
                             LeaveRepository leaveRepository,
                             TenantConfigRepository tenantConfigRepository,
                             TaskEngine taskEngine,
                             OutboxWriter outboxWriter,
                             ObjectMapper objectMapper) {
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

        // 跨租戶／不存在的單先判 404，才輪到引擎條件式更新擋下的 409——順序反了
        // 會把「別家的單」誤報成「狀態不符」，洩漏存在性
        Task task = taskRepository.findByTenantIdAndId(tenantId, taskId)
                .orElseThrow(() -> new NotFoundException("查無此任務"));

        // 自清自查禁令：伺服器端強制，不信前端把按鈕藏起來。
        // 「原清房者」＝任務當前 assignee——done 態的 assignee 就是完成清掃的人，
        // 退回重做也只回到同一人身上，這個等號在整條查房鏈上都成立
        if (inspector.staffId() != null && inspector.staffId().equals(task.getAssigneeId())) {
            throw new ForbiddenException("查房者不得為原清房者（E-07）");
        }

        if (!TaskType.CLEANING.equals(task.getType())) {
            throw new UnprocessableEntityException("查房只適用打掃任務，此單種類為 " + task.getType());
        }
        if (task.getRoomId() == null) {
            throw new UnprocessableEntityException("此任務未綁房間，無法查房");
        }

        List<SopItem> requiredItems = sopItemRepository
                .findByTenantIdAndAppliesToAndActiveTrueOrderBySortOrderAsc(tenantId, SopAppliesTo.INSPECTION);
        Map<String, Boolean> judgements = validateCoverage(request, requiredItems);

        Room room = roomRepository.findByTenantIdAndId(tenantId, task.getRoomId())
                .orElseThrow(() -> new NotFoundException("查無任務綁定的房間"));
        Actor actor = Actor.staff(inspector.role(), inspector.staffId());
        boolean allPass = judgements.values().stream().allMatch(Boolean::booleanValue);

        if (allPass) {
            // done→inspected 這個轉移靠引擎內的條件式更新擋（state ≠ done → 409），
            // 先讓這道更新通過才動房態——它擋下時這個副作用不該存在
            taskEngine.passInspection(tenantId, taskId, actor);
            updateHkState(room, RoomHkState.INSPECTED);
            recordInspection(tenantId, taskId, inspector.staffId(), InspectionResult.PASSED, judgements);
            return new InspectionResponse(taskId, InspectionResult.PASSED,
                    TaskState.INSPECTED, task.getRedoCount(), false);
        }

        // 任一項不過就整間退回。note 帶不過項目的名稱——
        // inspection_failed 事件是清房者唯一看得到「為什麼被退」的地方
        String failedLabels = String.join("、", requiredItems.stream()
                .filter(item -> !judgements.get(item.getItemKey()))
                .map(SopItem::getLabel)
                .toList());
        taskEngine.failInspection(tenantId, taskId, failedLabels, actor);
        // 勾記清空只是清掉 task_sop_checks 這張從事件整理出來、方便查詢的
        // 目前狀態副本：第一輪誰勾了什麼仍能從 task_events 回溯（SC-004：
        // 查房退回後第一輪的勾記紀錄仍須可追溯的驗收情境）
        taskSopCheckRepository.deleteByTenantIdAndTaskId(tenantId, taskId);
        updateHkState(room, RoomHkState.DIRTY);
        recordInspection(tenantId, taskId, inspector.staffId(), InspectionResult.FAILED, judgements);

        boolean returnedToPool = false;
        UUID cleanerId = task.getAssigneeId();
        if (cleanerId != null && cleanerUnavailable(tenantId, cleanerId)) {
            // 驗收情境 3：人不在就別讓單掛在他名下等到明天——同交易第二筆事件
            // accepted→pool，並排推播請同樓層的人優先接手
            int returnSeq = taskEngine.returnToPool(tenantId, taskId,
                    "查房退回時原清房者已下班或請假，自動退回待接池", actor);
            outboxWriter.enqueuePush(tenantId, taskId,
                    "task:" + taskId + ":returned_to_pool:seq:" + returnSeq,
                    returnedToPoolPushPayload(room, task, failedLabels));
            returnedToPool = true;
        }

        return new InspectionResponse(taskId, InspectionResult.FAILED,
                returnedToPool ? TaskState.POOL : TaskState.ACCEPTED,
                task.getRedoCount() + 1, returnedToPool);
    }

    /**
     * items 鍵集必須恰等於租戶查房項目全集（active 的 inspection
     * 項）。不只擋缺項——多出來的未知鍵、重複鍵、漏填的 pass 一樣 422：
     * inspections.items 要當 PMS 對帳與統計的素材，收進來的形狀必須封閉。
     */
    private Map<String, Boolean> validateCoverage(InspectionRequest request, List<SopItem> requiredItems) {
        if (requiredItems.isEmpty()) {
            throw new UnprocessableEntityException("租戶尚未設定查房項目，無法查房");
        }
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new UnprocessableEntityException("items 必填：須涵蓋租戶查房項目全集");
        }
        Map<String, Boolean> judgements = new LinkedHashMap<>();
        for (SopItem item : requiredItems) {
            judgements.put(item.getItemKey(), null);
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
        List<String> missing = judgements.entrySet().stream()
                .filter(entry -> entry.getValue() == null)
                .map(Map.Entry::getKey)
                .toList();
        if (!missing.isEmpty()) {
            throw new UnprocessableEntityException(
                    "items 須涵蓋租戶查房項目全集，缺: " + String.join(", ", missing));
        }
        return judgements;
    }

    /** 退回連動的觸發條件：原清房者已下班（shift_times 判定）或此刻在假（leaves 區間含現在）。 */
    private boolean cleanerUnavailable(long tenantId, UUID cleanerId) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean onLeave = leaveRepository
                .existsByTenantIdAndStaffIdAndFromAtLessThanEqualAndToAtGreaterThan(
                        tenantId, cleanerId, now, now);
        if (onLeave) {
            return true;
        }
        return !isWithinShift(tenantId, now.toLocalTime());
    }

    private boolean isWithinShift(long tenantId, LocalTime at) {
        return tenantConfigRepository.findByTenantIdAndKey(tenantId, "shift_times")
                .map(config -> {
                    try {
                        return ShiftTimes.coversTime(objectMapper.readTree(config.getValue()), at);
                    } catch (JacksonException e) {
                        // 誤觸發連動會把人還在場的單搶進池子，比漏觸發（主管手動
                        // requeue 即可補救）難收拾——解讀不了的設定一律不觸發
                        return true;
                    }
                })
                .orElse(true);
    }

    /** 查房記錄一次一列（history）：退回重查自然多列，「最後一次結果」＝created_at 最新那筆。 */
    private void recordInspection(long tenantId, UUID taskId, UUID inspectorId,
                                  String result, Map<String, Boolean> judgements) {
        ArrayNode items = objectMapper.createArrayNode();
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
        inspectionRepository.save(inspection);
    }

    private void updateHkState(Room room, String hkState) {
        room.setHkState(hkState);
        room.setUpdatedAt(OffsetDateTime.now());
        roomRepository.save(room);
    }

    /** 推播 payload 帶樓層與提示（spec 驗收情境 3「優先推播同樓層」）；消費者屬 P2，這裡只定好 wire format。 */
    private String returnedToPoolPushPayload(Room room, Task task, String failedLabels) {
        ObjectNode payload = objectMapper.createObjectNode();
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
