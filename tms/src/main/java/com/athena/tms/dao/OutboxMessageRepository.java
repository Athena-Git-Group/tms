package com.athena.tms.dao;

import com.athena.tms.entity.OutboxMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxMessageRepository
extends JpaRepository<OutboxMessage, Long> {
    public List<OutboxMessage> findByStatusOrderByAggregateKeyAscIdAsc(String var1);

    public Optional<OutboxMessage> findByTenantIdAndKindAndIdempotencyKey(Long var1, String var2, String var3);

    public boolean existsByAggregateKeyAndStatusAndIdLessThan(String var1, String var2, Long var3);
}
