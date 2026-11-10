package com.athena.tms.dao;

import com.athena.tms.entity.TaskSopCheck;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskSopCheckRepository
extends JpaRepository<TaskSopCheck, UUID> {
    public List<TaskSopCheck> findByTenantIdAndTaskId(Long var1, UUID var2);

    public void deleteByTenantIdAndTaskId(Long var1, UUID var2);
}
