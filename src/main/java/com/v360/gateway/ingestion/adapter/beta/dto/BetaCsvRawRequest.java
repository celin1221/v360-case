package com.v360.gateway.ingestion.adapter.beta.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload para envio de conteúdo bruto CSV do Cliente Beta")
public record BetaCsvRawRequest(
        @Schema(description = "Conteúdo CSV do arquivo de cabeçalho dos pedidos", example = "NUMERO_PEDIDO;FORNECEDOR_CNPJ;FORNECEDOR_RAZAO_SOCIAL;EMISSAO;SITUACAO;MOEDA\\n20260088412;12.345.678/0001-90;Distribuidora Horizonte Ltda;15/08/2026;EM ABERTO;BRL")
        String headerCsv,

        @Schema(description = "Conteúdo CSV do arquivo de itens dos pedidos", example = "NUMERO_PEDIDO;ITEM;CODIGO_MATERIAL;DESCRICAO;UNIDADE;QTD_PEDIDA;QTD_RECEBIDA;PRECO_UNITARIO\\n20260088412;1;MAT-77;Óleo de soja 900ml;UN;1.200,000;400,000;6,49")
        String itemsCsv
) {
}
