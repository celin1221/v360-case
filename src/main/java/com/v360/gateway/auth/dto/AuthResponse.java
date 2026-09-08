package com.v360.gateway.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Resposta de autenticação com token de acesso Bearer JWT")
public record AuthResponse(
        @Schema(description = "Token de acesso JWT assinado", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "Tipo do token", example = "Bearer")
        String tokenType,

        @Schema(description = "Tempo de expiração em segundos", example = "86400")
        long expiresIn,

        @Schema(description = "Código único do cliente (ex: CLI-ALFA-001 ou PLATFORM)", example = "PLATFORM")
        String tenantCode,

        @Schema(description = "Perfis de acesso concedidos", example = "[\"ROLE_PLATFORM\"]")
        List<String> roles
) {
    public static AuthResponse bearer(String token, long expiresIn, String tenantCode, List<String> roles) {
        return new AuthResponse(token, "Bearer", expiresIn, tenantCode, roles);
    }
}
