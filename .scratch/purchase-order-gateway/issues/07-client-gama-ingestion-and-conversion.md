# 07: Ingestão e Normalização do Cliente Gama Logística (Parte 2)

**What to build:** Ingestão do Cliente Gama Logística via `POST /api/v1/ingestion/gama`. Processa JSON achatado (flat), converte timestamps Unix em segundos para `LocalDate`, converte valores inteiros em centavos para `BigDecimal`, mapeia status numérico (1, 2, 3), e normaliza a unidade de compra em caixas (`CX`) para a unidade base de nota fiscal (`UN`) usando o `fator_conv`, preservando metadados comerciais para rastreabilidade.

**Blocked by:** 06-audit-report-and-phase-1-tag

**Status:** done

- [x] Componente `GamaJsonAdapter` realizando:
  - Agrupamento de itens por número de pedido (`ped`)
  - Conversão de `dt_criacao` (Unix timestamp em segundos) para `LocalDate`
  - Conversão de `preco_unit_centavos` para valor monetário (`/ 100`)
  - Mapeamento de `situacao` (`1`=OPEN, `2`=CLOSED, `3`=BLOCKED)
  - Cálculo de normalização para unidade base:
    - `quantityOrdered = qtd_ped * fator_conv`
    - `quantityReceived = qtd_rec * fator_conv`
    - `unitPrice = (preco_unit_centavos / 100) / fator_conv`
  - Armazenamento dos campos comerciais originais (`originalUom`, `originalQuantity`, `conversionFactor`)
- [x] Endpoint `POST /api/v1/ingestion/gama`
- [x] Inclusão dos dados do Gama no `DataInitializer` para carga no boot
- [x] Testes unitários do adapter e testes de conferência de notas fiscais contra pedidos do Cliente Gama
