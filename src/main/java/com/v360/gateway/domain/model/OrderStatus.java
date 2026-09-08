package com.v360.gateway.domain.model;

public enum OrderStatus {
    OPEN,
    CLOSED,
    BLOCKED;

    public static OrderStatus fromString(String status) {
        if (status == null) {
            return OPEN;
        }
        return switch (status.trim().toLowerCase()) {
            case "closed", "encerrado" -> CLOSED;
            case "blocked", "bloqueado" -> BLOCKED;
            case "open", "em aberto", "aberto" -> OPEN;
            default -> OPEN;
        };
    }

    public static OrderStatus fromAlfaStatus(String status) {
        return fromString(status);
    }

    public static OrderStatus fromBetaStatus(String status) {
        return fromString(status);
    }
}
