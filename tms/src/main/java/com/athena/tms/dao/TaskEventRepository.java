package com.athena.tms.dao;

import com.athena.tms.entity.TaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** 只進不改：save 新增可以，更新會被 DB 權限擋（42501）——這是刻意的防線不是 bug。 */
public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {

    /** 任務歷程：uq_task_events_task_seq，按 seq 重現這個任務的每一步。 */
    List<TaskEvent> findByTenantIdAndTaskIdOrderBySeqAsc(Long tenantId, UUID taskId);

    /**
     * 離線全表重放：SC-006（用全部事件在影子資料庫重建一份 tasks 目前狀態的
     * 查詢用副本，核對是否跟正式庫一致）靠這個方法依序撈出一個租戶的所有
     * 事件：idx_task_events_tenant_id。
     */
    List<TaskEvent> findByTenantIdOrderByIdAsc(Long tenantId);
}
