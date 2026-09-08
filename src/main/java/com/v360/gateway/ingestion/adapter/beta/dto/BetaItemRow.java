package com.v360.gateway.ingestion.adapter.beta.dto;

import java.math.BigDecimal;

/**
 * Parsed row representation of itens.csv from Cliente Beta Alimentos.
 */
public record BetaItemRow(
        String poNumber,
        int lineNumber,
        String materialCode,
        String description,
        String uom,
        BigDecimal quantityOrdered,
        BigDecimal quantityReceived,
        BigDecimal unitPrice
) {
}
