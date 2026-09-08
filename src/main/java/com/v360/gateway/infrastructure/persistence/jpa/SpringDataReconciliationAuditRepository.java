package com.v360.gateway.infrastructure.persistence.jpa;

import com.v360.gateway.domain.model.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataReconciliationAuditRepository extends JpaRepository<ReconciliationRecord, Long> {
    List<ReconciliationRecord> findByClientId(String clientId);
}
