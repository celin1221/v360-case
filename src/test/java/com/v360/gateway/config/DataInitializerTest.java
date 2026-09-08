package com.v360.gateway.config;

import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.data-initializer.enabled=true")
class DataInitializerTest {

    @Autowired
    private PurchaseOrderRepository repository;

    @Test
    @DisplayName("DataInitializer deve carregar automaticamente pedidos do Cliente Alfa e Beta no boot")
    void shouldSeedInitialDataOnBoot() {
        assertThat(repository.count()).isGreaterThanOrEqualTo(3);

        // Verifica pedido do Cliente Alfa
        Optional<PurchaseOrder> alfaOrderOpt = repository.findByClientIdAndPoNumber("CLI-ALFA-001", "4500001234");
        assertThat(alfaOrderOpt).isPresent();
        PurchaseOrder alfaOrder = alfaOrderOpt.get();
        assertThat(alfaOrder.getItems()).hasSize(2);
        assertThat(alfaOrder.getStatus()).isEqualTo(OrderStatus.OPEN);

        // Verifica pedidos do Cliente Beta
        Optional<PurchaseOrder> betaOrder1Opt = repository.findByClientIdAndPoNumber("CLI-BETA-002", "20260088412");
        assertThat(betaOrder1Opt).isPresent();
        assertThat(betaOrder1Opt.get().getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(betaOrder1Opt.get().getItems()).hasSize(2);

        Optional<PurchaseOrder> betaOrder2Opt = repository.findByClientIdAndPoNumber("CLI-BETA-002", "20260088413");
        assertThat(betaOrder2Opt).isPresent();
        assertThat(betaOrder2Opt.get().getStatus()).isEqualTo(OrderStatus.BLOCKED);
        assertThat(betaOrder2Opt.get().getItems()).hasSize(1);
    }
}
