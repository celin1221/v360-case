package com.v360.gateway.ingestion.adapter.beta;

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

class BetaCsvAdapterTest {

    private BetaCsvAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new BetaCsvAdapter();
    }

    @Test
    @DisplayName("Deve fazer o parse correto dos arquivos de cabeçalho e itens do Cliente Beta")
    void shouldParseValidSampleCsvsCorrectly() {
        String cabecalhoCsv = """
                NUMERO_PEDIDO;FORNECEDOR_CNPJ;FORNECEDOR_RAZAO_SOCIAL;EMISSAO;SITUACAO;MOEDA
                20260088412;12.345.678/0001-90;Distribuidora Horizonte Ltda;15/08/2026;EM ABERTO;BRL
                20260088413;98.765.432/0001-55;Frigorífico Boa Mesa S.A.;01/08/2026;BLOQUEADO;BRL
                """;

        String itensCsv = """
                NUMERO_PEDIDO;ITEM;CODIGO_MATERIAL;DESCRICAO;UNIDADE;QTD_PEDIDA;QTD_RECEBIDA;PRECO_UNITARIO
                20260088412;1;MAT-77;Óleo de soja 900ml;UN;1.200,000;400,000;6,49
                20260088412;2;MAT-78;Açúcar refinado 1kg;UN;500,000;0,000;4,15
                20260088413;1;MAT-91;Carne bovina dianteiro kg;KG;2.000,000;0,000;27,90
                """;

        List<PurchaseOrder> orders = adapter.parse(cabecalhoCsv, itensCsv);

        assertThat(orders).hasSize(2);

        // Pedido 1
        PurchaseOrder order1 = orders.stream()
                .filter(o -> "20260088412".equals(o.getPoNumber()))
                .findFirst()
                .orElseThrow();

        assertThat(order1.getClientId()).isEqualTo(BetaCsvAdapter.BETA_CLIENT_ID);
        assertThat(order1.getVendor().taxId()).isEqualTo("12345678000190");
        assertThat(order1.getVendor().name()).isEqualTo("Distribuidora Horizonte Ltda");
        assertThat(order1.getCreatedAt()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(order1.getCurrency()).isEqualTo("BRL");
        assertThat(order1.getItems()).hasSize(2);

        PurchaseOrderItem item1 = order1.getItems().get(0);
        assertThat(item1.getLineNumber()).isEqualTo(1);
        assertThat(item1.getMaterialCode()).isEqualTo("MAT-77");
        assertThat(item1.getDescription()).isEqualTo("Óleo de soja 900ml");
        assertThat(item1.getUom()).isEqualTo("UN");
        assertThat(item1.getQuantityOrdered()).isEqualByComparingTo(new BigDecimal("1200.000"));
        assertThat(item1.getQuantityReceived()).isEqualByComparingTo(new BigDecimal("400.000"));
        assertThat(item1.getUnitPrice()).isEqualByComparingTo(new BigDecimal("6.49"));
        assertThat(item1.getPendingQuantity()).isEqualByComparingTo(new BigDecimal("800.000"));

        PurchaseOrderItem item2 = order1.getItems().get(1);
        assertThat(item2.getLineNumber()).isEqualTo(2);
        assertThat(item2.getMaterialCode()).isEqualTo("MAT-78");
        assertThat(item2.getDescription()).isEqualTo("Açúcar refinado 1kg");
        assertThat(item2.getUom()).isEqualTo("UN");
        assertThat(item2.getQuantityOrdered()).isEqualByComparingTo(new BigDecimal("500.000"));
        assertThat(item2.getQuantityReceived()).isEqualByComparingTo(new BigDecimal("0.000"));
        assertThat(item2.getUnitPrice()).isEqualByComparingTo(new BigDecimal("4.15"));
        assertThat(item2.getPendingQuantity()).isEqualByComparingTo(new BigDecimal("500.000"));

        // Pedido 2
        PurchaseOrder order2 = orders.stream()
                .filter(o -> "20260088413".equals(o.getPoNumber()))
                .findFirst()
                .orElseThrow();

        assertThat(order2.getClientId()).isEqualTo(BetaCsvAdapter.BETA_CLIENT_ID);
        assertThat(order2.getVendor().taxId()).isEqualTo("98765432000155");
        assertThat(order2.getVendor().name()).isEqualTo("Frigorífico Boa Mesa S.A.");
        assertThat(order2.getCreatedAt()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.BLOCKED);
        assertThat(order2.getItems()).hasSize(1);

        PurchaseOrderItem item2_1 = order2.getItems().get(0);
        assertThat(item2_1.getLineNumber()).isEqualTo(1);
        assertThat(item2_1.getMaterialCode()).isEqualTo("MAT-91");
        assertThat(item2_1.getDescription()).isEqualTo("Carne bovina dianteiro kg");
        assertThat(item2_1.getUom()).isEqualTo("KG");
        assertThat(item2_1.getQuantityOrdered()).isEqualByComparingTo(new BigDecimal("2000.000"));
        assertThat(item2_1.getQuantityReceived()).isEqualByComparingTo(new BigDecimal("0.000"));
        assertThat(item2_1.getUnitPrice()).isEqualByComparingTo(new BigDecimal("27.90"));
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando arquivos forem nulos ou em branco")
    void shouldHandleEmptyOrNullCsvs() {
        assertThat(adapter.parse(null, null)).isEmpty();
        assertThat(adapter.parse("", "")).isEmpty();
        assertThat(adapter.parse("   ", "   ")).isEmpty();
    }

    @Test
    @DisplayName("Deve tratar pedido sem itens sem lançar erro")
    void shouldHandleOrderWithoutItems() {
        String cabecalhoCsv = """
                NUMERO_PEDIDO;FORNECEDOR_CNPJ;FORNECEDOR_RAZAO_SOCIAL;EMISSAO;SITUACAO;MOEDA
                20260099999;12.345.678/0001-90;Distribuidora Exemplo Ltda;10/08/2026;EM ABERTO;BRL
                """;
        String itensCsv = "";

        List<PurchaseOrder> orders = adapter.parse(cabecalhoCsv, itensCsv);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getPoNumber()).isEqualTo("20260099999");
        assertThat(orders.get(0).getItems()).isEmpty();
    }

    @Test
    @DisplayName("Deve sanitizar CNPJ removendo formatação e caracteres não numéricos")
    void shouldSanitizeCnpjCorrectly() {
        String cabecalhoCsv = """
                NUMERO_PEDIDO;FORNECEDOR_CNPJ;FORNECEDOR_RAZAO_SOCIAL;EMISSAO;SITUACAO;MOEDA
                20260011111;  12.345.678/0001-90  ;Fornecedor Teste;01/01/2026;ENCERRADO;BRL
                """;

        List<PurchaseOrder> orders = adapter.parse(cabecalhoCsv, null);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getVendor().taxId()).isEqualTo("12345678000190");
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderStatus.CLOSED);
    }

    @Test
    @DisplayName("Deve processar número com separador de milhar brasileiro sem casas decimais")
    void shouldParseNumberWithThousandsSeparatorOnly() {
        BigDecimal parsed = BetaCsvAdapter.parseBrazilianNumber("1.200", "TESTE");
        assertThat(parsed).isEqualByComparingTo(new BigDecimal("1200"));
    }

    @Test
    @DisplayName("Deve preservar ponto e vírgula dentro de aspas duplas conforme RFC 4180")
    void shouldPreserveSemicolonInsideQuotes() {
        String cabecalhoCsv = """
                NUMERO_PEDIDO;FORNECEDOR_CNPJ;FORNECEDOR_RAZAO_SOCIAL;EMISSAO;SITUACAO;MOEDA
                20260012345;12345678000190;"Razao Social; Com Ponto e Virgula Ltda";10/08/2026;EM ABERTO;BRL
                """;

        List<PurchaseOrder> orders = adapter.parse(cabecalhoCsv, null);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getVendor().name()).isEqualTo("Razao Social; Com Ponto e Virgula Ltda");
    }

    @Test
    @DisplayName("Deve lançar erro 400 ao encontrar número malformado")
    void shouldThrowOnMalformedNumber() {
        assertThatThrownBy(() -> BetaCsvAdapter.parseBrazilianNumber("abc", "QTD_PEDIDA"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Valor numérico inválido no campo 'QTD_PEDIDA': abc");
    }

    @Test
    @DisplayName("Deve lançar erro 400 ao encontrar data fora do formato DD/MM/YYYY")
    void shouldThrowOnInvalidDateFormat() {
        assertThatThrownBy(() -> BetaCsvAdapter.parseBrazilianDate("2026-08-15"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Data de emissão inválida ou fora do padrão DD/MM/YYYY");
    }

    @Test
    @DisplayName("Deve lançar erro 400 ao encontrar situação de pedido inválida")
    void shouldThrowOnInvalidOrderStatus() {
        assertThatThrownBy(() -> BetaCsvAdapter.parseBetaStatus("CANCELADO"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Situação de pedido inválida para o Cliente Beta: 'CANCELADO'");
    }

    @Test
    @DisplayName("Deve lançar erro 400 ao encontrar itens órfãos sem pedido no cabeçalho")
    void shouldThrowOnOrphanItemsWithoutMatchingHeader() {
        String cabecalhoCsv = """
                NUMERO_PEDIDO;FORNECEDOR_CNPJ;FORNECEDOR_RAZAO_SOCIAL;EMISSAO;SITUACAO;MOEDA
                20260088412;12345678000190;Fornecedor Teste;15/08/2026;EM ABERTO;BRL
                """;

        String itensCsv = """
                NUMERO_PEDIDO;ITEM;CODIGO_MATERIAL;DESCRICAO;UNIDADE;QTD_PEDIDA;QTD_RECEBIDA;PRECO_UNITARIO
                20260099999;1;MAT-77;Item Orfao;UN;10,000;0,000;5,00
                """;

        assertThatThrownBy(() -> adapter.parse(cabecalhoCsv, itensCsv))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("sem cabeçalho correspondente");
    }
}
