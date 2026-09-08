package com.v360.gateway.domain.port;

import com.v360.gateway.domain.model.ReconciliationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ReconciliationAuditRepository {

    ReconciliationRecord save(ReconciliationRecord record);

    Optional<ReconciliationRecord> findById(Long id);

    List<ReconciliationRecord> findAll();

    Page<ReconciliationRecord> findAll(Pageable pageable);

    List<ReconciliationRecord> findByClientId(String clientId);

    long count();

    void deleteAll();
}
