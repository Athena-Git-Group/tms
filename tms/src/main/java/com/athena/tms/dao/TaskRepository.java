package com.athena.tms.dao;

import com.athena.tms.entity.Task;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository
extends JpaRepository<Task, UUID> {
    public Optional<Task> findByTenantIdAndId(Long var1, UUID var2);

    public List<Task> findByTenantIdAndDeptAndStateOrderByCreatedAtAsc(Long var1, String var2, String var3);

    public List<Task> findByTenantIdAndAssigneeIdAndStateInOrderByCreatedAtAsc(Long var1, UUID var2, Collection<String> var3);

    public List<Task> findByTenantIdAndRoomIdOrderByCreatedAtDesc(Long var1, UUID var2);

    public List<Task> findByTenantIdAndReservationIdOrderByCreatedAtDesc(Long var1, UUID var2);
}
