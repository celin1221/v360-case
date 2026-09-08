package com.v360.gateway.ingestion.adapter.alfa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record AlfaPayloadDto(
        @JsonProperty("purchase_orders")
        List<AlfaOrderDto> purchaseOrders
) {
    public record AlfaOrderDto(
            @JsonProperty("po_number")
            String poNumber,

            @JsonProperty("created_at")
            String createdAt,

            @JsonProperty("status")
            String status,

            @JsonProperty("currency")
            String currency,

            @JsonProperty("vendor")
            AlfaVendorDto vendor,

            @JsonProperty("items")
            List<AlfaItemDto> items
    ) {}

    public record AlfaVendorDto(
            @JsonProperty("tax_id")
            String taxId,

            @JsonProperty("name")
            String name
    ) {}

    public record AlfaItemDto(
            @JsonProperty("line")
            Integer line,

            @JsonProperty("material")
            String material,

            @JsonProperty("description")
            String description,

            @JsonProperty("uom")
            String uom,

            @JsonProperty("quantity_ordered")
            BigDecimal quantityOrdered,

            @JsonProperty("quantity_received")
            BigDecimal quantityReceived,

            @JsonProperty("unit_price")
            BigDecimal unitPrice
    ) {}
}
