package com.athena.tms.dao;

import com.athena.tms.entity.TaskSopCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskSopCheckRepository extends JpaRepository<TaskSopCheck, UUID> {

    List<TaskSopCheck> findByTenantIdAndTaskId(Long tenantId, UUID taskId);

    Optional<TaskSopCheck> findByTenantIdAndTaskIdAndItemKey(Long tenantId, UUID taskId, String itemKey);

    /**
     * 查房退回歸零重來可以整批刪：task_sop_checks 只是從事件紀錄整理出來、
     * 方便查詢的目前狀態副本，權威的勾記歷史在 task_events 的 sop_checked
     * 事件裡，不會跟著消失。
     */
    void deleteByTenantIdAndTaskId(Long tenantId, UUID taskId);
}
