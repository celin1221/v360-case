package com.v360.gateway.infrastructure.persistence.inmemory;

import com.v360.gateway.domain.model.ReconciliationRecord;
import com.v360.gateway.domain.port.ReconciliationAuditRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile("in-memory")
public class InMemoryReconciliationAuditRepository implements ReconciliationAuditRepository {

    private final Map<Long, ReconciliationRecord> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public ReconciliationRecord save(ReconciliationRecord record) {
        if (record.getId() == null) {
            record.setId(idGenerator.getAndIncrement());
        }
        storage.put(record.getId(), record);
        return record;
    }

    @Override
    public Optional<ReconciliationRecord> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<ReconciliationRecord> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Page<ReconciliationRecord> findAll(Pageable pageable) {
        List<ReconciliationRecord> all = new ArrayList<>(storage.values());
        all.sort(Comparator.comparing(ReconciliationRecord::getReconciledAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());

        if (start > all.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, all.size());
        }

        return new PageImpl<>(all.subList(start, end), pageable, all.size());
    }

    @Override
    public List<ReconciliationRecord> findByClientId(String clientId) {
        return storage.values().stream()
                .filter(r -> Objects.equals(r.getClientId(), clientId))
                .toList();
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public void deleteAll() {
        storage.clear();
    }
}
