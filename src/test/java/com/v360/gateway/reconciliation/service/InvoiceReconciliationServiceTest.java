package com.v360.gateway.reconciliation.service;

import com.v360.gateway.common.exception.ApiException;
import com.v360.gateway.domain.model.*;
import com.v360.gateway.infrastructure.persistence.inmemory.InMemoryPurchaseOrderRepository;
import com.v360.gateway.infrastructure.persistence.inmemory.InMemoryReconciliationAuditRepository;
import com.v360.gateway.reconciliation.dto.InvoiceItemRequest;
import com.v360.gateway.reconciliation.dto.InvoiceReconciliationRequest;
import com.v360.gateway.reconciliation.dto.ReconciliationReportResponse;
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

    @Test
    @DisplayName("Deve persistir registro de auditoria completo quando houver divergências e status REJECTED")
    void shouldPersistDivergencesInAuditRepositoryWhenReconciliationIsRejected() {
        ClientPrincipal platformPrincipal = new ClientPrincipal("v360-platform", null, List.of("ROLE_PLATFORM"));
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-DIV-001",
                "4500001234",
                "23456789000101",
                List.of(
                        // Preço unitário acordado é 45.90, nota vem com 55.00 (+9.10)
                        new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10.00"), new BigDecimal("55.00"), null),
                        // Material não cadastrado
                        new InvoiceItemRequest(2, "MAT-INEXISTENTE", new BigDecimal("5.00"), new BigDecimal("10.00"), null)
                )
        );

        ReconciliationResponse response = service.reconcile(platformPrincipal, request);

        assertThat(response.status()).isEqualTo(ReconciliationStatus.REJECTED);
        assertThat(response.divergences()).hasSize(2);

        // Verifica que o registro de auditoria foi gravado no repositório em memória
        assertThat(auditRepository.count()).isEqualTo(1);
        ReconciliationRecord persisted = auditRepository.findAll().get(0);
        assertThat(persisted.getStatus()).isEqualTo(ReconciliationStatus.REJECTED);
        assertThat(persisted.getDivergences()).hasSize(2);
        assertThat(persisted.getDivergences()).extracting(ReconciliationDivergence::getCode)
                .containsExactlyInAnyOrder(DivergenceType.PRICE_MISMATCH, DivergenceType.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("Deve detectar estouro de saldo pendente quando o mesmo material é dividido em múltiplas linhas")
    void shouldDetectCumulativeQuantityExceedingPendingBalanceAcrossMultipleLines() {
        ClientPrincipal platformPrincipal = new ClientPrincipal("v360-platform", null, List.of("ROLE_PLATFORM"));
        // Saldo pendente do MAT-1001 é 40 (100 pedidos - 60 recebidos)
        // Linha 1 fatura 25, linha 2 fatura 20 -> total 45 (> 40)
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-SPLIT-001",
                "4500001234",
                "23456789000101",
                List.of(
                        new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("25.00"), new BigDecimal("45.90"), null),
                        new InvoiceItemRequest(2, "MAT-1001", new BigDecimal("20.00"), new BigDecimal("45.90"), null)
                )
        );

        ReconciliationResponse response = service.reconcile(platformPrincipal, request);

        assertThat(response.status()).isEqualTo(ReconciliationStatus.REJECTED);
        assertThat(response.divergences()).hasSize(1);
        assertThat(response.divergences().get(0).code()).isEqualTo(DivergenceType.QUANTITY_EXCEEDS_PENDING_BALANCE);
        assertThat(response.divergences().get(0).actualValue()).isEqualTo("45");
        assertThat(response.divergences().get(0).expectedValue()).isEqualTo("40");
        assertThat(response.divergences().get(0).difference()).isEqualTo("+5");
    }

    @Test
    @DisplayName("Deve gerar identificador de fallback quando invoiceNumber for omitido")
    void shouldGenerateFallbackInvoiceNumberWhenOmitted() {
        ClientPrincipal clientAlfaPrincipal = new ClientPrincipal("alfa-client", "CLI-ALFA-001", List.of("ROLE_CLIENT"));
        InvoiceReconciliationRequest requestWithoutInvoiceNumber = new InvoiceReconciliationRequest(
                null,
                null, // Omitido
                "4500001234",
                "23456789000101",
                List.of(new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10"), new BigDecimal("45.90"), null))
        );

        ReconciliationResponse response = service.reconcile(clientAlfaPrincipal, requestWithoutInvoiceNumber);

        assertThat(response.invoiceNumber()).isEqualTo("INV-4500001234");
        ReconciliationRecord persisted = auditRepository.findAll().get(0);
        assertThat(persisted.getInvoiceNumber()).isEqualTo("INV-4500001234");
    }

    @Test
    @DisplayName("ROLE_PLATFORM deve gerar relatório consolidado global e por cliente")
    void shouldGenerateReportForPlatformRole() {
        ClientPrincipal platformPrincipal = new ClientPrincipal("v360-platform", null, List.of("ROLE_PLATFORM"));

        // 1. Alfa: 1 aprovado
        auditRepository.save(new ReconciliationRecord(
                "CLI-ALFA-001", "4500001234", "NF-1", "23456789000101",
                ReconciliationStatus.APPROVED, null, List.of()
        ));

        // 2. Alfa: 1 rejeitado com PRICE_MISMATCH
        auditRepository.save(new ReconciliationRecord(
                "CLI-ALFA-001", "4500001234", "NF-2", "23456789000101",
                ReconciliationStatus.REJECTED, null,
                List.of(new ReconciliationDivergence(DivergenceType.PRICE_MISMATCH, 1, "MAT-1001", "Preço", "45.90", "50.00", "+4.10"))
        ));

        // 3. Beta: 1 aprovado
        auditRepository.save(new ReconciliationRecord(
                "CLI-BETA-002", "20260088412", "NF-3", "12345678000190",
                ReconciliationStatus.APPROVED, null, List.of()
        ));

        // Relatório Global da Plataforma
        ReconciliationReportResponse globalReport = service.generateReport(platformPrincipal, null);
        assertThat(globalReport.totalReconciliations()).isEqualTo(3);
        assertThat(globalReport.totalApproved()).isEqualTo(2);
        assertThat(globalReport.totalRejected()).isEqualTo(1);
        assertThat(globalReport.approvalRatePercentage()).isEqualByComparingTo(new BigDecimal("66.67"));
        assertThat(globalReport.divergenceCounts()).containsEntry(DivergenceType.PRICE_MISMATCH, 1L);

        // Relatório Filtrado para Cliente Alfa
        ReconciliationReportResponse alfaReport = service.generateReport(platformPrincipal, "CLI-ALFA-001");
        assertThat(alfaReport.totalReconciliations()).isEqualTo(2);
        assertThat(alfaReport.totalApproved()).isEqualTo(1);
        assertThat(alfaReport.totalRejected()).isEqualTo(1);
        assertThat(alfaReport.approvalRatePercentage()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("ROLE_CLIENT deve visualizar relatório estritamente de seu próprio tenant e ser barrado no cross-tenant")
    void shouldEnforceTenantIsolationOnReportForClientRole() {
        ClientPrincipal clientAlfaPrincipal = new ClientPrincipal("alfa-client", "CLI-ALFA-001", List.of("ROLE_CLIENT"));

        auditRepository.save(new ReconciliationRecord(
                "CLI-ALFA-001", "4500001234", "NF-1", "23456789000101",
                ReconciliationStatus.APPROVED, null, List.of()
        ));
        auditRepository.save(new ReconciliationRecord(
                "CLI-BETA-002", "20260088412", "NF-3", "12345678000190",
                ReconciliationStatus.APPROVED, null, List.of()
        ));

        // Relatório do Alfa (automático)
        ReconciliationReportResponse report = service.generateReport(clientAlfaPrincipal, null);
        assertThat(report.totalReconciliations()).isEqualTo(1);
        assertThat(report.clientId()).isEqualTo("CLI-ALFA-001");

        // Tentativa de acessar relatório do Beta -> 403 ACCESS_DENIED
        assertThatThrownBy(() -> service.generateReport(clientAlfaPrincipal, "CLI-BETA-002"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("ACCESS_DENIED");
                });
    }
}
