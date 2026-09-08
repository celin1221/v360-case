package com.v360.gateway.domain.model;

public enum OrderStatus {
    OPEN,
    CLOSED,
    BLOCKED;

    public static OrderStatus fromAlfaStatus(String status) {
        if (status == null) {
            return OPEN;
        }
        return switch (status.trim().toLowerCase()) {
            case "closed", "encerrado" -> CLOSED;
            case "blocked", "bloqueado" -> BLOCKED;
            default -> OPEN;
        };
    }
}
