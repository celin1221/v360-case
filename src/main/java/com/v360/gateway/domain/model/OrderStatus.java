package com.v360.gateway.domain.model;

/**
 * Canonical purchase order status.
 * Per ADR-0001, external client-specific representations are mapped by client adapters.
 */
public enum OrderStatus {
    OPEN,
    CLOSED,
    BLOCKED
}
