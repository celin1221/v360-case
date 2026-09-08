package com.v360.gateway.ingestion.adapter.alfa;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v360.gateway.common.exception.ApiException;
import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.PurchaseOrderItem;
import com.v360.gateway.domain.model.Vendor;
import com.v360.gateway.ingestion.adapter.alfa.dto.AlfaPayloadDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class AlfaJsonAdapter {

    public static final String ALFA_CLIENT_ID = "CLI-ALFA-001";
    private final ObjectMapper objectMapper;

    public AlfaJsonAdapter() {
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<PurchaseOrder> parse(String jsonContent) {
        try {
            AlfaPayloadDto payload = objectMapper.readValue(jsonContent, AlfaPayloadDto.class);
            if (payload == null || payload.purchaseOrders() == null) {
                return List.of();
            }

            List<PurchaseOrder> orders = new ArrayList<>();
            for (AlfaPayloadDto.AlfaOrderDto orderDto : payload.purchaseOrders()) {
                orders.add(mapToPurchaseOrder(orderDto));
            }
            return orders;
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MALFORMED_ALFA_PAYLOAD",
                    "Erro ao deserializar payload JSON do Cliente Alfa: " + e.getMessage()
            );
        }
    }

    private PurchaseOrder mapToPurchaseOrder(AlfaPayloadDto.AlfaOrderDto dto) {
        LocalDate createdAt = dto.createdAt() != null ? LocalDate.parse(dto.createdAt().trim()) : LocalDate.now();
        OrderStatus status = parseStatus(dto.status());
        String currency = dto.currency() != null ? dto.currency().trim() : "BRL";

        Vendor vendor = dto.vendor() != null
                ? new Vendor(dto.vendor().taxId(), dto.vendor().name())
                : new Vendor("", "");

        PurchaseOrder order = new PurchaseOrder(
                ALFA_CLIENT_ID,
                dto.poNumber() != null ? dto.poNumber().trim() : "",
                createdAt,
                status,
                currency,
                vendor
        );

        if (dto.items() != null) {
            for (AlfaPayloadDto.AlfaItemDto itemDto : dto.items()) {
                PurchaseOrderItem item = new PurchaseOrderItem(
                        itemDto.line() != null ? itemDto.line() : 1,
                        itemDto.material() != null ? itemDto.material().trim() : "",
                        itemDto.description() != null ? itemDto.description().trim() : "",
                        itemDto.uom() != null ? itemDto.uom().trim() : "UN",
                        itemDto.quantityOrdered(),
                        itemDto.quantityReceived(),
                        itemDto.unitPrice()
                );
                order.addItem(item);
            }
        }

        return order;
    }

    private OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return OrderStatus.OPEN;
        }
        return switch (status.trim().toLowerCase()) {
            case "open" -> OrderStatus.OPEN;
            case "closed" -> OrderStatus.CLOSED;
            case "blocked" -> OrderStatus.BLOCKED;
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATUS",
                    "Status de pedido inválido para o Cliente Alfa: " + status
            );
        };
    }
}
