package com.v360.gateway.ingestion.adapter.gama;

import com.v360.gateway.common.exception.ApiException;
import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.PurchaseOrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GamaJsonAdapter - Parser e Normalizador Canônico do Cliente Gama Logística")
class GamaJsonAdapterTest {

    private GamaJsonAdapter adapter;

    private static final String SAMPLE_GAMA_JSON = """
            [
              {
                "ped": "GL-778",
                "item": 1,
                "cnpj_fornecedor": "34567890000112",
                "nome_fornecedor": "Transportes Ideal ME",
                "dt_criacao": 1786752000,
                "cod_mat": "TRP-01",
                "desc_mat": "Pallet de madeira",
                "um": "CX",
                "fator_conv": 12,
                "qtd_ped": 10,
                "qtd_rec": 2,
                "preco_unit_centavos": 120000,
                "situacao": 1
              },
              {
                "ped": "GL-778",
                "item": 2,
                "cnpj_fornecedor": "34567890000112",
                "nome_fornecedor": "Transportes Ideal ME",
                "dt_criacao": 1786752000,
                "cod_mat": "TRP-09",
                "desc_mat": "Caixa organizadora",
                "um": "CX",
                "fator_conv": 3,
                "qtd_ped": 4,
                "qtd_rec": 0,
                "preco_unit_centavos": 10000,
                "situacao": 1
              },
              {
                "ped": "GL-779",
                "item": 1,
                "cnpj_fornecedor": "56789012000134",
                "nome_fornecedor": "Armazéns Rio Claro Ltda",
                "dt_criacao": 1784160000,
                "cod_mat": "ARM-10",
                "desc_mat": "Estrado metálico",
                "um": "UN",
                "fator_conv": 1,
                "qtd_ped": 100,
                "qtd_rec": 100,
                "preco_unit_centavos": 3500,
                "situacao": 2
              }
            ]
            """;

    @BeforeEach
    void setUp() {
        adapter = new GamaJsonAdapter();
    }

    @Test
    @DisplayName("Deve agrupar registros flat por pedido preservando a ordem")
    void shouldParseAndGroupFlatJsonSuccessfully() {
        List<PurchaseOrder> orders = adapter.parse(SAMPLE_GAMA_JSON);

        assertThat(orders).hasSize(2);

        PurchaseOrder order1 = orders.get(0);
        assertThat(order1.getPoNumber()).isEqualTo("GL-778");
        assertThat(order1.getClientId()).isEqualTo("CLI-GAMA-003");
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(order1.getCreatedAt()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(order1.getVendor().taxId()).isEqualTo("34567890000112");
        assertThat(order1.getVendor().name()).isEqualTo("Transportes Ideal ME");
        assertThat(order1.getItems()).hasSize(2);

        PurchaseOrder order2 = orders.get(1);
        assertThat(order2.getPoNumber()).isEqualTo("GL-779");
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(order2.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("Deve normalizar caixas (CX) para unidades (UN) e calcular quantidades e preços proporcionais")
    void shouldNormalizeBaseUnitAndPackagingMetadata() {
        List<PurchaseOrder> orders = adapter.parse(SAMPLE_GAMA_JSON);
        PurchaseOrder order = orders.get(0);

        // Item 1: 10 CX com fator 12 -> 120 UN. Rec: 2 CX * 12 -> 24 UN. Preço: 1200 / 12 = 100 UN.
        PurchaseOrderItem item1 = order.getItems().get(0);
        assertThat(item1.getLineNumber()).isEqualTo(1);
        assertThat(item1.getMaterialCode()).isEqualTo("TRP-01");
        assertThat(item1.getUom()).isEqualTo("UN");
        assertThat(item1.getQuantityOrdered()).isEqualByComparingTo("120.0000");
        assertThat(item1.getQuantityReceived()).isEqualByComparingTo("24.0000");
        assertThat(item1.getPendingQuantity()).isEqualByComparingTo("96.0000");
        assertThat(item1.getUnitPrice()).isEqualByComparingTo("100.0000");

        // Metadados comerciais preservados (ADR-0003)
        assertThat(item1.getOriginalUom()).isEqualTo("CX");
        assertThat(item1.getOriginalQuantity()).isEqualByComparingTo("10.0000");
        assertThat(item1.getConversionFactor()).isEqualByComparingTo("12.0000");

        // Item 2: 4 CX com fator 3 -> 12 UN. Preço: 100 / 3 = 33.3333 UN.
        PurchaseOrderItem item2 = order.getItems().get(1);
        assertThat(item2.getLineNumber()).isEqualTo(2);
        assertThat(item2.getMaterialCode()).isEqualTo("TRP-09");
        assertThat(item2.getQuantityOrdered()).isEqualByComparingTo("12.0000");
        assertThat(item2.getQuantityReceived()).isEqualByComparingTo("0.0000");
        assertThat(item2.getPendingQuantity()).isEqualByComparingTo("12.0000");
        assertThat(item2.getUnitPrice()).isEqualByComparingTo("33.3333");
        assertThat(item2.getOriginalUom()).isEqualTo("CX");
        assertThat(item2.getOriginalQuantity()).isEqualByComparingTo("4.0000");
        assertThat(item2.getConversionFactor()).isEqualByComparingTo("3.0000");
    }

    @Test
    @DisplayName("Deve mapear status numérico: 1=OPEN, 2=CLOSED, 3=BLOCKED")
    void shouldMapNumericStatusCorrectly() {
        String json = """
                [
                  {"ped": "G1", "item": 1, "cod_mat": "M1", "um": "UN", "dt_criacao": 1786752000, "situacao": 1, "fator_conv": 1},
                  {"ped": "G2", "item": 1, "cod_mat": "M1", "um": "UN", "dt_criacao": 1786752000, "situacao": 2, "fator_conv": 1},
                  {"ped": "G3", "item": 1, "cod_mat": "M1", "um": "UN", "dt_criacao": 1786752000, "situacao": 3, "fator_conv": 1}
                ]
                """;

        List<PurchaseOrder> orders = adapter.parse(json);
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(orders.get(1).getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(orders.get(2).getStatus()).isEqualTo(OrderStatus.BLOCKED);
    }

    @Test
    @DisplayName("Deve lançar erro 400 caso a unidade de medida original 'um' seja ausente")
    void shouldThrowWhenOriginalUomIsMissing() {
        String json = """
                [
                  {"ped": "G-ERR", "item": 1, "cod_mat": "M1", "dt_criacao": 1786752000, "situacao": 1, "fator_conv": 1}
                ]
                """;

        assertThatThrownBy(() -> adapter.parse(json))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("A unidade de medida comercial original ('um') é obrigatória");
    }

    @Test
    @DisplayName("Deve lançar erro 400 caso a data de criação 'dt_criacao' seja ausente ou inválida")
    void shouldThrowWhenCreationDateIsMissingOrInvalid() {
        String json = """
                [
                  {"ped": "G-ERR", "item": 1, "cod_mat": "M1", "um": "CX", "situacao": 1, "fator_conv": 1}
                ]
                """;

        assertThatThrownBy(() -> adapter.parse(json))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("A data de criação ('dt_criacao') é obrigatória e deve ser um timestamp Unix válido em segundos");
    }

    @Test
    @DisplayName("Deve lançar erro 400 caso o fator de conversão seja nulo ou menor ou igual a zero")
    void shouldThrowWhenConversionFactorIsInvalid() {
        String json = """
                [
                  {"ped": "G-ERR", "item": 1, "cod_mat": "M1", "um": "CX", "dt_criacao": 1786752000, "situacao": 1, "fator_conv": 0}
                ]
                """;

        assertThatThrownBy(() -> adapter.parse(json))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("O fator de conversão ('fator_conv') deve ser maior que zero");
    }

    @Test
    @DisplayName("Deve lançar erro 400 caso a situação numérica seja inválida")
    void shouldThrowWhenStatusIsInvalid() {
        String json = """
                [
                  {"ped": "G-ERR", "item": 1, "cod_mat": "M1", "um": "CX", "dt_criacao": 1786752000, "situacao": 99, "fator_conv": 1}
                ]
                """;

        assertThatThrownBy(() -> adapter.parse(json))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Order Status inválido para o Cliente Gama");
    }

    @Test
    @DisplayName("Deve lançar erro 400 para JSON malformado")
    void shouldThrowOnMalformedJson() {
        assertThatThrownBy(() -> adapter.parse("{ invalid json }"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Malformed flat JSON payload for Client Gama");
    }

    @Test
    @DisplayName("Deve retornar lista vazia para payload vazio ou nulo")
    void shouldReturnEmptyListForEmptyPayload() {
        assertThat(adapter.parse(null)).isEmpty();
        assertThat(adapter.parse("")).isEmpty();
        assertThat(adapter.parse("[]")).isEmpty();
    }
}
