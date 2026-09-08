package com.v360.gateway.infrastructure.persistence.inmemory;

import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.PurchaseOrderItem;
import com.v360.gateway.domain.model.Vendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPurchaseOrderRepositoryTest {

    private InMemoryPurchaseOrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPurchaseOrderRepository();
    }

    @Test
    @DisplayName("Deve salvar novo pedido e gerar ID sequencial em memória")
    void shouldSaveAndGenerateIdInMemory() {
        PurchaseOrder order = new PurchaseOrder(
                "CLI-ALFA-001",
                "PO-100",
                LocalDate.now(),
                OrderStatus.OPEN,
                "BRL",
                new Vendor("12345678000199", "Fornecedor Teste")
        );
        order.addItem(new PurchaseOrderItem(10, "MAT-1", "Desc", "UN", new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("5.0")));

        PurchaseOrder saved = repository.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Deve realizar upsert idempotente ao salvar pedido com mesmo clientId e poNumber")
    void shouldPerformIdempotentUpsertInMemory() {
        PurchaseOrder order1 = new PurchaseOrder(
                "CLI-ALFA-001",
                "PO-100",
                LocalDate.now(),
                OrderStatus.OPEN,
                "BRL",
                new Vendor("12345678000199", "Fornecedor Teste")
        );
        order1.addItem(new PurchaseOrderItem(10, "MAT-1", "Desc", "UN", new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("5.0")));
        repository.save(order1);

        PurchaseOrder order2 = new PurchaseOrder(
                "CLI-ALFA-001",
                "PO-100",
                LocalDate.now(),
                OrderStatus.CLOSED,
                "BRL",
                new Vendor("12345678000199", "Fornecedor Teste Atualizado")
        );
        order2.addItem(new PurchaseOrderItem(10, "MAT-1", "Desc", "UN", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("5.0")));
        repository.save(order2);

        assertThat(repository.count()).isEqualTo(1);

        Optional<PurchaseOrder> found = repository.findByClientIdAndPoNumber("CLI-ALFA-001", "PO-100");
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(found.get().getItems().getFirst().getQuantityReceived()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    @DisplayName("Deve filtrar corretamente por onlyPendingBalance em memória")
    void shouldFilterByPendingBalanceInMemory() {
        PurchaseOrder withPending = new PurchaseOrder("CLI-ALFA-001", "PO-PENDING", LocalDate.now(), OrderStatus.OPEN, "BRL", new Vendor("111", "V1"));
        withPending.addItem(new PurchaseOrderItem(10, "MAT-1", "Desc", "UN", new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("10.0")));
        repository.save(withPending);

        PurchaseOrder zeroPending = new PurchaseOrder("CLI-ALFA-001", "PO-ZERO", LocalDate.now(), OrderStatus.OPEN, "BRL", new Vendor("222", "V2"));
        zeroPending.addItem(new PurchaseOrderItem(10, "MAT-2", "Desc", "UN", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10.0")));
        repository.save(zeroPending);

        Page<PurchaseOrder> page = repository.findWithFilters(
                null,
                null,
                null,
                true,
                PageRequest.of(0, 10)
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getPoNumber()).isEqualTo("PO-PENDING");
    }
}
