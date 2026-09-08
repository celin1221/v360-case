package com.v360.gateway.infrastructure.persistence.inmemory;

import com.v360.gateway.domain.model.DivergenceType;
import com.v360.gateway.domain.model.ReconciliationDivergence;
import com.v360.gateway.domain.model.ReconciliationRecord;
import com.v360.gateway.domain.model.ReconciliationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryReconciliationAuditRepositoryTest {

    private InMemoryReconciliationAuditRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryReconciliationAuditRepository();
    }

    @Test
    @DisplayName("Deve salvar e recuperar registro de auditoria com lista de divergências embeddadas")
    void shouldSaveAndRetrieveReconciliationRecordWithDivergences() {
        ReconciliationDivergence div = new ReconciliationDivergence(
                DivergenceType.PRICE_MISMATCH,
                1,
                "MAT-1001",
                "Preço unitário divergente",
                "45.90",
                "50.00",
                "+4.10"
        );

        ReconciliationRecord record = new ReconciliationRecord(
                "CLI-ALFA-001",
                "4500001234",
                "NF-1001",
                "23456789000101",
                ReconciliationStatus.REJECTED,
                Instant.now(),
                List.of(div)
        );

        ReconciliationRecord saved = repository.save(record);

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.count()).isEqualTo(1);

        Optional<ReconciliationRecord> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getClientId()).isEqualTo("CLI-ALFA-001");
        assertThat(found.get().getInvoiceNumber()).isEqualTo("NF-1001");
        assertThat(found.get().getStatus()).isEqualTo(ReconciliationStatus.REJECTED);
        assertThat(found.get().getDivergences()).hasSize(1);
        assertThat(found.get().getDivergences().get(0).getCode()).isEqualTo(DivergenceType.PRICE_MISMATCH);
    }

    @Test
    @DisplayName("Deve filtrar registros de auditoria por clientId")
    void shouldFilterByClientId() {
        repository.save(new ReconciliationRecord(
                "CLI-ALFA-001", "PO-1", "NF-1", "12345678000100", ReconciliationStatus.APPROVED, Instant.now(), List.of()
        ));
        repository.save(new ReconciliationRecord(
                "CLI-BETA-002", "PO-2", "NF-2", "98765432000199", ReconciliationStatus.REJECTED, Instant.now(), List.of()
        ));

        List<ReconciliationRecord> alfaRecords = repository.findByClientId("CLI-ALFA-001");
        assertThat(alfaRecords).hasSize(1);
        assertThat(alfaRecords.get(0).getPoNumber()).isEqualTo("PO-1");

        List<ReconciliationRecord> betaRecords = repository.findByClientId("CLI-BETA-002");
        assertThat(betaRecords).hasSize(1);
        assertThat(betaRecords.get(0).getPoNumber()).isEqualTo("PO-2");
    }

    @Test
    @DisplayName("Deve paginar registros ordenando por data de conferência decrescente")
    void shouldPaginateReconciliationRecords() {
        repository.save(new ReconciliationRecord(
                "CLI-ALFA-001", "PO-1", "NF-1", "123", ReconciliationStatus.APPROVED, Instant.now().minusSeconds(100), List.of()
        ));
        repository.save(new ReconciliationRecord(
                "CLI-ALFA-001", "PO-2", "NF-2", "123", ReconciliationStatus.APPROVED, Instant.now(), List.of()
        ));

        Page<ReconciliationRecord> page = repository.findAll(PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).getPoNumber()).isEqualTo("PO-2");
    }
}
