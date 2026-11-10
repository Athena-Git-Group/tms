package com.athena.tms.dao;

import com.athena.tms.entity.IntegrationApiKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationApiKeyRepository
extends JpaRepository<IntegrationApiKey, UUID> {
    public Optional<IntegrationApiKey> findByKeyHash(String var1);
}
