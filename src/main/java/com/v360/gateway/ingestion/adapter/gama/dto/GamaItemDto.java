package com.v360.gateway.ingestion.adapter.gama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Registro de item flat do Cliente Gama Logística")
public record GamaItemDto(
        @JsonProperty("ped")
        @Schema(description = "Número do pedido", example = "GL-778")
        String ped,

        @JsonProperty("item")
        @Schema(description = "Número da linha/item", example = "1")
        Integer item,

        @JsonProperty("cnpj_fornecedor")
        @Schema(description = "CNPJ do fornecedor", example = "34567890000112")
        String cnpjFornecedor,

        @JsonProperty("nome_fornecedor")
        @Schema(description = "Razão social do fornecedor", example = "Transportes Ideal ME")
        String nomeFornecedor,

        @JsonProperty("dt_criacao")
        @Schema(description = "Timestamp Unix da criação em segundos", example = "1786752000")
        Long dtCriacao,

        @JsonProperty("cod_mat")
        @Schema(description = "Código do material", example = "TRP-01")
        String codMat,

        @JsonProperty("desc_mat")
        @Schema(description = "Descrição do material", example = "Pallet de madeira")
        String descMat,

        @JsonProperty("um")
        @Schema(description = "Unidade de compra original (ex: CX)", example = "CX")
        String um,

        @JsonProperty("fator_conv")
        @Schema(description = "Fator de conversão para a unidade base", example = "12")
        BigDecimal fatorConv,

        @JsonProperty("qtd_ped")
        @Schema(description = "Quantidade pedida na embalagem original", example = "10")
        BigDecimal qtdPed,

        @JsonProperty("qtd_rec")
        @Schema(description = "Quantidade recebida na embalagem original", example = "2")
        BigDecimal qtdRec,

        @JsonProperty("preco_unit_centavos")
        @Schema(description = "Preço unitário original em centavos", example = "120000")
        Long precoUnitCentavos,

        @JsonProperty("situacao")
        @Schema(description = "Situação numérica: 1=OPEN, 2=CLOSED, 3=BLOCKED", example = "1")
        Integer situacao
) {
}
