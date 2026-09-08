package com.v360.gateway.common.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<String> details
) {
    public ErrorResponse(int status, String error, String code, String message, String path) {
        this(Instant.now(), status, error, code, message, path, List.of());
    }

    public ErrorResponse(int status, String error, String code, String message, String path, List<String> details) {
        this(Instant.now(), status, error, code, message, path, details);
    }
}
