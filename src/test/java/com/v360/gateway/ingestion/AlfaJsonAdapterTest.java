package com.v360.gateway.ingestion;

import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.PurchaseOrderItem;
import com.v360.gateway.ingestion.adapter.alfa.AlfaJsonAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlfaJsonAdapterTest {

    private AlfaJsonAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AlfaJsonAdapter();
    }

    @Test
    @DisplayName("Deve converter com sucesso o JSON aninhado do Cliente Alfa para o modelo canônico")
    void shouldParseValidAlfaJsonToCanonicalOrders() {
        String alfaJson = """
                {
                  "purchase_orders": [
                    {
                      "po_number": "4500001234",
                      "created_at": "2026-08-05",
                      "status": "open",
                      "currency": "BRL",
                      "vendor": {
                        "tax_id": "23456789000101",
                        "name": "Metalúrgica São Jorge S.A."
                      },
                      "items": [
                        {
                          "line": 10,
                          "material": "MAT-1001",
                          "description": "Chapa de aço 2mm",
                          "uom": "UN",
                          "quantity_ordered": 100,
                          "quantity_received": 60,
                          "unit_price": 45.9
                        },
                        {
                          "line": 20,
                          "material": "MAT-1002",
                          "description": "Perfil U 3m",
                          "uom": "UN",
                          "quantity_ordered": 50,
                          "quantity_received": 0,
                          "unit_price": 128.75
                        }
                      ]
                    }
                  ]
                }
                """;

        List<PurchaseOrder> orders = adapter.parse(alfaJson);

        assertThat(orders).hasSize(1);
        PurchaseOrder order = orders.getFirst();

        assertThat(order.getClientId()).isEqualTo("CLI-ALFA-001");
        assertThat(order.getPoNumber()).isEqualTo("4500001234");
        assertThat(order.getCreatedAt()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(order.getCurrency()).isEqualTo("BRL");

        assertThat(order.getVendor()).isNotNull();
        assertThat(order.getVendor().taxId()).isEqualTo("23456789000101");
        assertThat(order.getVendor().name()).isEqualTo("Metalúrgica São Jorge S.A.");

        assertThat(order.getItems()).hasSize(2);

        PurchaseOrderItem item1 = order.getItems().get(0);
        assertThat(item1.getLineNumber()).isEqualTo(10);
        assertThat(item1.getMaterialCode()).isEqualTo("MAT-1001");
        assertThat(item1.getDescription()).isEqualTo("Chapa de aço 2mm");
        assertThat(item1.getUom()).isEqualTo("UN");
        assertThat(item1.getQuantityOrdered()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(item1.getQuantityReceived()).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(item1.getUnitPrice()).isEqualByComparingTo(new BigDecimal("45.90"));
        assertThat(item1.getPendingQuantity()).isEqualByComparingTo(new BigDecimal("40"));

        PurchaseOrderItem item2 = order.getItems().get(1);
        assertThat(item2.getLineNumber()).isEqualTo(20);
        assertThat(item2.getMaterialCode()).isEqualTo("MAT-1002");
        assertThat(item2.getPendingQuantity()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    @DisplayName("Deve mapear corretamente status closed e blocked")
    void shouldMapDifferentStatusesCorrectly() {
        String json = """
                {
                  "purchase_orders": [
                    {
                      "po_number": "PO-CLOSED",
                      "created_at": "2026-08-01",
                      "status": "closed",
                      "vendor": { "tax_id": "11111111000111", "name": "V1" },
                      "items": []
                    },
                    {
                      "po_number": "PO-BLOCKED",
                      "created_at": "2026-08-02",
                      "status": "blocked",
                      "vendor": { "tax_id": "22222222000122", "name": "V2" },
                      "items": []
                    }
                  ]
                }
                """;

        List<PurchaseOrder> orders = adapter.parse(json);

        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(orders.get(1).getStatus()).isEqualTo(OrderStatus.BLOCKED);
    }

    @Test
    @DisplayName("Deve lançar exceção para payload JSON malformado")
    void shouldThrowExceptionForMalformedJson() {
        assertThatThrownBy(() -> adapter.parse("{ invalid json"))
                .isInstanceOf(RuntimeException.class);
    }
}
