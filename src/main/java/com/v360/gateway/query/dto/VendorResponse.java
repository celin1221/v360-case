package com.v360.gateway.query.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados do fornecedor do pedido de compra")
public record VendorResponse(
        @Schema(description = "CNPJ sanitizado (apenas 14 dígitos)", example = "23456789000101")
        String taxId,

        @Schema(description = "Razão social do fornecedor", example = "Metalúrgica São Jorge S.A.")
        String name
) {
}
