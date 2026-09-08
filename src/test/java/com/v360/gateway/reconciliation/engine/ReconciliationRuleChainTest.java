package com.v360.gateway.reconciliation.engine;

import com.v360.gateway.domain.model.*;
import com.v360.gateway.reconciliation.dto.InvoiceItemRequest;
import com.v360.gateway.reconciliation.dto.InvoiceReconciliationRequest;
import com.v360.gateway.reconciliation.engine.rules.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationRuleChainTest {

    private ReconciliationRuleChain ruleChain;
    private PurchaseOrder sampleOrder;

    @BeforeEach
    void setUp() {
        ruleChain = new ReconciliationRuleChain(List.of(
                new PurchaseOrderExistenceRule(),
                new VendorMatchRule(),
                new OrderStatusRule(),
                new ItemExistenceRule(),
                new ItemPendingBalanceRule(),
                new ItemPriceToleranceRule()
        ));

        sampleOrder = new PurchaseOrder(
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
                new BigDecimal("60.0000"), // pending: 40.0000
                new BigDecimal("45.90")
        ));
        sampleOrder.addItem(new PurchaseOrderItem(
                20,
                "MAT-1002",
                "Perfil U 3m",
                "UN",
                new BigDecimal("50.0000"),
                new BigDecimal("0.0000"), // pending: 50.0000
                new BigDecimal("128.75")
        ));
    }

    @Test
    @DisplayName("Deve aprovar quando a nota fiscal confere 100% com o pedido")
    void shouldApproveWhenInvoiceMatchesOrder() {
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-1001",
                "4500001234",
                "23.456.789/0001-01",
                List.of(
                        new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("40.0000"), new BigDecimal("45.90"), null),
                        new InvoiceItemRequest(2, "MAT-1002", new BigDecimal("20.0000"), new BigDecimal("128.75"), null)
                )
        );

        ReconciliationContext context = new ReconciliationContext("CLI-ALFA-001", request, sampleOrder);
        ruleChain.execute(context);

        assertThat(context.hasDivergences()).isFalse();
        assertThat(context.getStatus()).isEqualTo(ReconciliationStatus.APPROVED);
    }

    @Test
    @DisplayName("Deve aprovar quando diferença de preço está dentro da tolerância de até R$ 0,01")
    void shouldApproveWhenPriceDifferenceIsWithinTolerance() {
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-1002",
                "4500001234",
                "23456789000101",
                List.of(
                        // Preço no pedido: 45.90 -> nota com 45.91 (diferença de 0.01 aceita)
                        new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10.0000"), new BigDecimal("45.91"), null),
                        // Preço no pedido: 128.75 -> nota com 128.74 (diferença de -0.01 aceita)
                        new InvoiceItemRequest(2, "MAT-1002", new BigDecimal("5.0000"), new BigDecimal("128.74"), null)
                )
        );

        ReconciliationContext context = new ReconciliationContext("CLI-ALFA-001", request, sampleOrder);
        ruleChain.execute(context);

        assertThat(context.hasDivergences()).isFalse();
        assertThat(context.getStatus()).isEqualTo(ReconciliationStatus.APPROVED);
    }

    @Test
    @DisplayName("Deve rejeitar com short-circuit quando o pedido de compra não existe")
    void shouldRejectWithShortCircuitWhenOrderNotFound() {
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-9999",
                "9999999999",
                "23456789000101",
                List.of(new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10"), new BigDecimal("45.90"), null))
        );

        ReconciliationContext context = new ReconciliationContext("CLI-ALFA-001", request, null);
        ruleChain.execute(context);

        assertThat(context.hasDivergences()).isTrue();
        assertThat(context.getStatus()).isEqualTo(ReconciliationStatus.REJECTED);
        assertThat(context.getDivergences()).hasSize(1);
        ReconciliationDivergence div = context.getDivergences().get(0);
        assertThat(div.getCode()).isEqualTo(DivergenceType.ORDER_NOT_FOUND);
        assertThat(div.getActualValue()).isEqualTo("NÃO ENCONTRADO");
    }

    @Test
    @DisplayName("Deve rejeitar quando o CNPJ do fornecedor na nota fiscal divergir do pedido")
    void shouldRejectWhenVendorMismatch() {
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-1003",
                "4500001234",
                "99.999.999/0001-99", // CNPJ diferente
                List.of(new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10"), new BigDecimal("45.90"), null))
        );

        ReconciliationContext context = new ReconciliationContext("CLI-ALFA-001", request, sampleOrder);
        ruleChain.execute(context);

        assertThat(context.getStatus()).isEqualTo(ReconciliationStatus.REJECTED);
        assertThat(context.getDivergences()).anyMatch(d -> d.getCode() == DivergenceType.VENDOR_MISMATCH);
    }

    @Test
    @DisplayName("Deve rejeitar quando o pedido de compra estiver bloqueado")
    void shouldRejectWhenOrderIsBlocked() {
        sampleOrder.setStatus(OrderStatus.BLOCKED);

        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-1004",
                "4500001234",
                "23456789000101",
                List.of(new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10"), new BigDecimal("45.90"), null))
        );

        ReconciliationContext context = new ReconciliationContext("CLI-ALFA-001", request, sampleOrder);
        ruleChain.execute(context);

        assertThat(context.getStatus()).isEqualTo(ReconciliationStatus.REJECTED);
        assertThat(context.getDivergences()).anyMatch(d -> d.getCode() == DivergenceType.ORDER_BLOCKED);
    }

    @Test
    @DisplayName("Deve rejeitar quando o pedido de compra estiver encerrado")
    void shouldRejectWhenOrderIsClosed() {
        sampleOrder.setStatus(OrderStatus.CLOSED);

        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-1005",
                "4500001234",
                "23456789000101",
                List.of(new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10"), new BigDecimal("45.90"), null))
        );

        ReconciliationContext context = new ReconciliationContext("CLI-ALFA-001", request, sampleOrder);
        ruleChain.execute(context);

        assertThat(context.getStatus()).isEqualTo(ReconciliationStatus.REJECTED);
        assertThat(context.getDivergences()).anyMatch(d -> d.getCode() == DivergenceType.ORDER_CLOSED);
    }

    @Test
    @DisplayName("Deve rejeitar e apontar item específico quando o material não existe no pedido")
    void shouldRejectAndPointExactItemWhenMaterialNotFound() {
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-1006",
                "4500001234",
                "23456789000101",
                List.of(
                        new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10"), new BigDecimal("45.90"), null),
                        new InvoiceItemRequest(2, "MAT-9999", new BigDecimal("5"), new BigDecimal("10.00"), null) // Não existe
                )
        );

        ReconciliationContext context = new ReconciliationContext("CLI-ALFA-001", request, sampleOrder);
        ruleChain.execute(context);

        assertThat(context.getStatus()).isEqualTo(ReconciliationStatus.REJECTED);
        assertThat(context.getDivergences()).hasSize(1);
        ReconciliationDivergence div = context.getDivergences().get(0);
        assertThat(div.getCode()).isEqualTo(DivergenceType.ITEM_NOT_FOUND);
        assertThat(div.getLineNumber()).isEqualTo(2);
        assertThat(div.getMaterialCode()).isEqualTo("MAT-9999");
        assertThat(div.getDescription()).contains("Linha 2");
    }

    @Test
    @DisplayName("Deve rejeitar e apontar linha, material e diferença quando quantidade exceder saldo pendente")
    void shouldRejectAndPointLineAndDifferenceWhenQuantityExceedsPendingBalance() {
        // Saldo pendente do MAT-1001 é 40 (100 pedidos - 60 recebidos)
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-1007",
                "4500001234",
                "23456789000101",
                List.of(
                        new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("45.0000"), new BigDecimal("45.90"), null) // Excede por 5
                )
        );

        ReconciliationContext context = new ReconciliationContext("CLI-ALFA-001", request, sampleOrder);
        ruleChain.execute(context);

        assertThat(context.getStatus()).isEqualTo(ReconciliationStatus.REJECTED);
        assertThat(context.getDivergences()).hasSize(1);
        ReconciliationDivergence div = context.getDivergences().get(0);
        assertThat(div.getCode()).isEqualTo(DivergenceType.QUANTITY_EXCEEDS_PENDING_BALANCE);
        assertThat(div.getLineNumber()).isEqualTo(1);
        assertThat(div.getMaterialCode()).isEqualTo("MAT-1001");
        assertThat(div.getExpectedValue()).isEqualTo("40");
        assertThat(div.getActualValue()).isEqualTo("45");
        assertThat(div.getDifference()).isEqualTo("+5");
    }

    @Test
    @DisplayName("Deve rejeitar e apontar linha, material e diferença quando preço divergir acima de R$ 0,01")
    void shouldRejectAndPointLineAndDifferenceWhenPriceMismatchExceedsTolerance() {
        // Preço acordado no pedido: 45.90 -> Nota enviada com 45.92 (diferença de 0.02)
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-1008",
                "4500001234",
                "23456789000101",
                List.of(
                        new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10.0000"), new BigDecimal("45.92"), null)
                )
        );

        ReconciliationContext context = new ReconciliationContext("CLI-ALFA-001", request, sampleOrder);
        ruleChain.execute(context);

        assertThat(context.getStatus()).isEqualTo(ReconciliationStatus.REJECTED);
        assertThat(context.getDivergences()).hasSize(1);
        ReconciliationDivergence div = context.getDivergences().get(0);
        assertThat(div.getCode()).isEqualTo(DivergenceType.PRICE_MISMATCH);
        assertThat(div.getLineNumber()).isEqualTo(1);
        assertThat(div.getMaterialCode()).isEqualTo("MAT-1001");
        assertThat(div.getExpectedValue()).isEqualTo("45.90");
        assertThat(div.getActualValue()).isEqualTo("45.92");
        assertThat(div.getDifference()).isEqualTo("+0.02");
    }

    @Test
    @DisplayName("Deve capturar exaustivamente múltiplas divergências em múltiplos itens simultaneamente")
    void shouldExhaustivelyCaptureMultipleDivergencesAcrossItems() {
        sampleOrder.setStatus(OrderStatus.BLOCKED);

        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-1009",
                "4500001234",
                "11111111000111", // 1. VENDOR_MISMATCH
                List.of(
                        // 2. QUANTITY_EXCEEDS_PENDING_BALANCE + 3. PRICE_MISMATCH na linha 1
                        new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("100.0000"), new BigDecimal("55.00"), null),
                        // 4. ITEM_NOT_FOUND na linha 2
                        new InvoiceItemRequest(2, "MAT-DESCONHECIDO", new BigDecimal("10.0000"), new BigDecimal("100.00"), null)
                )
        );

        ReconciliationContext context = new ReconciliationContext("CLI-ALFA-001", request, sampleOrder);
        ruleChain.execute(context);

        assertThat(context.getStatus()).isEqualTo(ReconciliationStatus.REJECTED);
        // Espera-se 5 divergências: VENDOR_MISMATCH, ORDER_BLOCKED, ITEM_NOT_FOUND (linha 2), QUANTITY_EXCEEDS_PENDING_BALANCE (linha 1), PRICE_MISMATCH (linha 1)
        assertThat(context.getDivergences()).hasSize(5);
        assertThat(context.getDivergences()).extracting(ReconciliationDivergence::getCode)
                .containsExactlyInAnyOrder(
                        DivergenceType.VENDOR_MISMATCH,
                        DivergenceType.ORDER_BLOCKED,
                        DivergenceType.ITEM_NOT_FOUND,
                        DivergenceType.QUANTITY_EXCEEDS_PENDING_BALANCE,
                        DivergenceType.PRICE_MISMATCH
                );
    }
}
