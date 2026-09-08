package com.v360.gateway.query.dto;

import com.v360.gateway.domain.model.PurchaseOrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Item detalhado de um pedido de compra normalizado no modelo canônico V360")
public record PurchaseOrderItemResponse(
        @Schema(description = "Número da linha/item", example = "10")
        Integer lineNumber,

        @Schema(description = "Código do material ou serviço", example = "MAT-1001")
        String materialCode,

        @Schema(description = "Descrição detalhada do material", example = "Chapa de aço 2mm")
        String description,

        @Schema(description = "Unidade de medida canônica (ex: UN, KG)", example = "UN")
        String uom,

        @Schema(description = "Quantidade total comprada/pedida", example = "100.0000")
        BigDecimal quantityOrdered,

        @Schema(description = "Quantidade já recebida/entregue", example = "60.0000")
        BigDecimal quantityReceived,

        @Schema(description = "Saldo pendente a receber (calculado como max(0, quantityOrdered - quantityReceived))", example = "40.0000")
        BigDecimal pendingQuantity,

        @Schema(description = "Preço unitário acordado", example = "45.9000")
        BigDecimal unitPrice,

        @Schema(description = "Valor total do item (quantityOrdered * unitPrice)", example = "4590.0000")
        BigDecimal totalPrice,

        @Schema(description = "Unidade de medida de compra comercial original (se aplicável, ex: CX)", example = "CX")
        String originalUom,

        @Schema(description = "Quantidade comprada na embalagem comercial original", example = "10.0000")
        BigDecimal originalQuantity,

        @Schema(description = "Fator de conversão aplicado da embalagem comercial para a unidade canônica", example = "12.0000")
        BigDecimal conversionFactor
) {
    public static PurchaseOrderItemResponse fromDomain(PurchaseOrderItem item) {
        return new PurchaseOrderItemResponse(
                item.getLineNumber(),
                item.getMaterialCode(),
                item.getDescription(),
                item.getUom(),
                item.getQuantityOrdered(),
                item.getQuantityReceived(),
                item.getPendingQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getOriginalUom(),
                item.getOriginalQuantity(),
                item.getConversionFactor()
        );
    }
}
