package com.athena.tms.dao;

import com.athena.tms.entity.TaskEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskEventRepository
extends JpaRepository<TaskEvent, Long> {
    public List<TaskEvent> findByTenantIdAndTaskIdOrderBySeqAsc(Long var1, UUID var2);

    public List<TaskEvent> findByTenantIdOrderByIdAsc(Long var1);
}
