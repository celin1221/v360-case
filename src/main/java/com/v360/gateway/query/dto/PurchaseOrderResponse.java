package com.v360.gateway.query.dto;

import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Representação canônica unificada de um pedido de compra na plataforma V360")
public record PurchaseOrderResponse(
        @Schema(description = "Identificador interno do registro no banco de dados", example = "1")
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

        @Schema(description = "Valor total acumulado de todos os itens do pedido", example = "11027.5000")
        BigDecimal totalAmount,

        @Schema(description = "Indica se o pedido ainda possui saldo pendente a receber em pelo menos um item", example = "true")
        boolean hasPendingBalance,

        @Schema(description = "Quantidade total de linhas/itens do pedido", example = "2")
        int totalItems,

        @Schema(description = "Lista detalhada de itens com seus respectivos saldos pendentes", implementation = PurchaseOrderItemResponse.class)
        List<PurchaseOrderItemResponse> items
) {
    public static PurchaseOrderResponse fromDomain(PurchaseOrder order) {
        VendorResponse vendor = order.getVendor() != null
                ? new VendorResponse(order.getVendor().taxId(), order.getVendor().name())
                : new VendorResponse("", "");

        List<PurchaseOrderItemResponse> items = order.getItems() != null
                ? order.getItems().stream().map(PurchaseOrderItemResponse::fromDomain).toList()
                : List.of();

        BigDecimal total = items.stream()
                .map(PurchaseOrderItemResponse::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PurchaseOrderResponse(
                order.getId(),
                order.getClientId(),
                order.getPoNumber(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getCurrency(),
                vendor,
                total,
                order.hasPendingBalance(),
                items.size(),
                items
        );
    }
}
