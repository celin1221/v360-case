package com.v360.gateway.infrastructure.persistence.jpa;

import com.v360.gateway.domain.model.ReconciliationRecord;
import com.v360.gateway.domain.port.ReconciliationAuditRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!in-memory")
@Transactional
public class JpaReconciliationAuditRepositoryAdapter implements ReconciliationAuditRepository {

    private final SpringDataReconciliationAuditRepository springDataRepo;

    public JpaReconciliationAuditRepositoryAdapter(SpringDataReconciliationAuditRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public ReconciliationRecord save(ReconciliationRecord record) {
        return springDataRepo.save(record);
    }

    @Override
    public Optional<ReconciliationRecord> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<ReconciliationRecord> findAll() {
        return springDataRepo.findAll();
    }

    @Override
    public Page<ReconciliationRecord> findAll(Pageable pageable) {
        return springDataRepo.findAll(pageable);
    }

    @Override
    public List<ReconciliationRecord> findByClientId(String clientId) {
        return springDataRepo.findByClientId(clientId);
    }

    @Override
    public long count() {
        return springDataRepo.count();
    }

    @Override
    public void deleteAll() {
        springDataRepo.deleteAll();
    }
}
