package com.athena.tms.dao;

import com.athena.tms.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 查詢方法照 spec §5.1 存取樣式開，每個都有對應索引，不另開掃全表的查法。
 * 一律帶 tenant 參數（第一道牆）；狀態轉移要用的條件式 UPDATE——前提條件
 * 直接寫進 WHERE，不符就一列也不改——屬任務引擎，不在這裡；repository
 * 只讀與單純寫入。
 */
public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByTenantIdAndId(Long tenantId, UUID id);

    /** 待接池與看板：idx_tasks_dept_state。先進先出，久等的單先被看到。 */
    List<Task> findByTenantIdAndDeptAndStateOrderByCreatedAtAsc(Long tenantId, String dept, String state);

    /** 個人在手：idx_tasks_assignee_state。 */
    List<Task> findByTenantIdAndAssigneeIdAndStateInOrderByCreatedAtAsc(
            Long tenantId, UUID assigneeId, Collection<String> states);

    /** 單房歷程：idx_tasks_room_created。新單在前——翻往事通常只關心最近。 */
    List<Task> findByTenantIdAndRoomIdOrderByCreatedAtDesc(Long tenantId, UUID roomId);

    /** 住客本次住宿需求：idx_tasks_reservation_created。以訂單過濾，不按房號（防掃碼換房串單）。 */
    List<Task> findByTenantIdAndReservationIdOrderByCreatedAtDesc(Long tenantId, UUID reservationId);
}
