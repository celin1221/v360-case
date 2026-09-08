package com.v360.gateway.reconciliation.service;

import com.v360.gateway.common.exception.ApiException;
import com.v360.gateway.domain.model.*;
import com.v360.gateway.infrastructure.persistence.inmemory.InMemoryPurchaseOrderRepository;
import com.v360.gateway.infrastructure.persistence.inmemory.InMemoryReconciliationAuditRepository;
import com.v360.gateway.reconciliation.dto.InvoiceItemRequest;
import com.v360.gateway.reconciliation.dto.InvoiceReconciliationRequest;
import com.v360.gateway.reconciliation.dto.ReconciliationResponse;
import com.v360.gateway.reconciliation.engine.ReconciliationRuleChain;
import com.v360.gateway.reconciliation.engine.rules.*;
import com.v360.gateway.security.ClientPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceReconciliationServiceTest {

    private InMemoryPurchaseOrderRepository poRepository;
    private InMemoryReconciliationAuditRepository auditRepository;
    private InvoiceReconciliationService service;

    @BeforeEach
    void setUp() {
        poRepository = new InMemoryPurchaseOrderRepository();
        auditRepository = new InMemoryReconciliationAuditRepository();

        ReconciliationRuleChain ruleChain = new ReconciliationRuleChain(List.of(
                new PurchaseOrderExistenceRule(),
                new VendorMatchRule(),
                new OrderStatusRule(),
                new ItemExistenceRule(),
                new ItemPendingBalanceRule(),
                new ItemPriceToleranceRule()
        ));

        service = new InvoiceReconciliationService(poRepository, auditRepository, ruleChain);

        PurchaseOrder sampleOrder = new PurchaseOrder(
                "CLI-ALFA-001",
                "4500001234",
                LocalDate.of(2026, 8, 5),
                OrderStatus.OPEN,
                "BRL",
                new Vendor("23456789000101", "Metalúrgica São Jorge S.A.")
        );
        sampleOrder.addItem(new PurchaseOrderItem(
                10,
                "MAT-1001",
                "Chapa de aço 2mm",
                "UN",
                new BigDecimal("100.0000"),
                new BigDecimal("60.0000"),
                new BigDecimal("45.90")
        ));
        poRepository.save(sampleOrder);
    }

    @Test
    @DisplayName("ROLE_PLATFORM deve exigir clientId obrigatório na requisição")
    void shouldThrowWhenPlatformRoleOmitsClientId() {
        ClientPrincipal platformPrincipal = new ClientPrincipal("v360-platform", null, List.of("ROLE_PLATFORM"));
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                null,
                "NF-100",
                "4500001234",
                "23456789000101",
                List.of(new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10"), new BigDecimal("45.90"), null))
        );

        assertThatThrownBy(() -> service.reconcile(platformPrincipal, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("MISSING_CLIENT_ID");
                });
    }

    @Test
    @DisplayName("ROLE_CLIENT deve ser barrado com 403 ao tentar conferir nota de outro cliente")
    void shouldThrowWhenClientRoleAttemptsCrossTenantReconciliation() {
        ClientPrincipal clientAlfaPrincipal = new ClientPrincipal("alfa-client", "CLI-ALFA-001", List.of("ROLE_CLIENT"));
        InvoiceReconciliationRequest crossRequest = new InvoiceReconciliationRequest(
                "CLI-BETA-002", // Tentativa cross-tenant
                "NF-100",
                "4500001234",
                "23456789000101",
                List.of(new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10"), new BigDecimal("45.90"), null))
        );

        assertThatThrownBy(() -> service.reconcile(clientAlfaPrincipal, crossRequest))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("ACCESS_DENIED");
                });
    }

    @Test
    @DisplayName("ROLE_CLIENT deve usar automaticamente seu tenantCode e persistir auditoria")
    void shouldDefaultToTenantCodeForClientRoleAndPersistAudit() {
        ClientPrincipal clientAlfaPrincipal = new ClientPrincipal("alfa-client", "CLI-ALFA-001", List.of("ROLE_CLIENT"));
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                null, // omitido, deve usar CLI-ALFA-001
                "NF-100",
                "4500001234",
                "23456789000101",
                List.of(new InvoiceItemRequest(null, "MAT-1001", new BigDecimal("10"), new BigDecimal("45.90"), null))
        );

        ReconciliationResponse response = service.reconcile(clientAlfaPrincipal, request);

        assertThat(response).isNotNull();
        assertThat(response.clientId()).isEqualTo("CLI-ALFA-001");
        assertThat(response.status()).isEqualTo(ReconciliationStatus.APPROVED);
        assertThat(response.divergences()).isEmpty();

        assertThat(auditRepository.count()).isEqualTo(1);
    }
}
