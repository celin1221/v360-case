package com.v360.gateway.ingestion.adapter.beta.dto;

import com.v360.gateway.domain.model.OrderStatus;

import java.time.LocalDate;

/**
 * Parsed row representation of cabecalho.csv from Cliente Beta Alimentos.
 */
public record BetaHeaderRow(
        String poNumber,
        String vendorTaxId,
        String vendorName,
        LocalDate issueDate,
        OrderStatus status,
        String currency
) {
}
