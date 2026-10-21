package com.athena.tms.dao;

import com.athena.tms.entity.IntegrationApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IntegrationApiKeyRepository extends JpaRepository<IntegrationApiKey, UUID> {

    /** 本表受 RLS 保護：呼叫前必須先設定 TenantContext，否則一律查無列而被誤判為無效 key。 */
    Optional<IntegrationApiKey> findByKeyHash(String keyHash);
}
