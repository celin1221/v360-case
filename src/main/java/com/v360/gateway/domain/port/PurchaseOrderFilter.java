package com.v360.gateway.domain.port;

import com.v360.gateway.domain.model.OrderStatus;

/**
 * Encapsulates canonical query criteria for purchase order searches.
 */
public record PurchaseOrderFilter(
        String clientId,
        String vendorTaxId,
        OrderStatus status,
        Boolean onlyPendingBalance
) {
}
