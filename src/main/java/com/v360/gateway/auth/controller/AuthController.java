package com.v360.gateway.auth.controller;

import com.v360.gateway.auth.dto.AuthRequest;
import com.v360.gateway.auth.dto.AuthResponse;
import com.v360.gateway.security.ClientCredentialsAuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Endpoints de autenticação Machine-to-Machine (OAuth 2.0 Client Credentials)")
public class AuthController {

    private final ClientCredentialsAuthenticationService authService;

    public AuthController(ClientCredentialsAuthenticationService authService) {
        this.authService = authService;
    }

    @PostMapping("/token")
    @Operation(
            summary = "Emitir token de acesso OAuth 2.0 (Client Credentials)",
            description = "Recebe clientId e clientSecret e emite um token Bearer JWT assinado contendo os escopos de tenant e papéis autorizados.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token emitido com sucesso",
                            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
            }
    )
    public ResponseEntity<AuthResponse> token(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.authenticate(request);
        return ResponseEntity.ok(response);
    }
}
