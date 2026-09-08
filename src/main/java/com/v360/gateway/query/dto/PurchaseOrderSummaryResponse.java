package com.v360.gateway.query.dto;

import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Resumo canônico de pedido de compra para listagens paginadas de alta performance")
public record PurchaseOrderSummaryResponse(
        @Schema(description = "Identificador interno no banco de dados", example = "1")
        Long id,

        @Schema(description = "Identificador imutável do cliente contratante", example = "CLI-ALFA-001")
        String clientId,

        @Schema(description = "Número comercial do pedido de compra", example = "4500001234")
        String poNumber,

        @Schema(description = "Data de emissão/criação do pedido", example = "2026-08-05")
        LocalDate createdAt,

        @Schema(description = "Situação unificada do pedido: OPEN, CLOSED, BLOCKED", example = "OPEN")
        OrderStatus status,

        @Schema(description = "Moeda do pedido", example = "BRL")
        String currency,

        @Schema(description = "Dados do fornecedor", implementation = VendorResponse.class)
        VendorResponse vendor,

        @Schema(description = "Valor total acumulado do pedido", example = "11027.5000")
        BigDecimal totalAmount,

        @Schema(description = "Indica se o pedido ainda possui saldo pendente a receber", example = "true")
        boolean hasPendingBalance,

        @Schema(description = "Quantidade de itens contidos no pedido", example = "2")
        int totalItems
) {
    public static PurchaseOrderSummaryResponse fromDomain(PurchaseOrder order) {
        VendorResponse vendor = order.getVendor() != null
                ? new VendorResponse(order.getVendor().taxId(), order.getVendor().name())
                : new VendorResponse("", "");

        return new PurchaseOrderSummaryResponse(
                order.getId(),
                order.getClientId(),
                order.getPoNumber(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getCurrency(),
                vendor,
                order.getTotalAmount(),
                order.hasPendingBalance(),
                order.getItemCount()
        );
    }
}
