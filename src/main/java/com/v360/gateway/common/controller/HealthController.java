package com.v360.gateway.common.controller;

import com.v360.gateway.security.ClientPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Saúde & Diagnóstico", description = "Verificação de status do serviço e contexto de segurança autenticado")
public class HealthController {

    @GetMapping
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Verificar status e contexto autenticado",
            description = "Retorna o status da aplicação e os detalhes do cliente e papéis do token JWT fornecido."
    )
    public ResponseEntity<Map<String, Object>> health(@AuthenticationPrincipal ClientPrincipal client) {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString(),
                "clientId", client != null ? client.clientId() : "unknown",
                "tenantCode", client != null ? client.tenantCode() : "unknown",
                "roles", client != null ? client.roles() : List.of()
        ));
    }
}
