package com.v360.gateway.security;

import com.v360.gateway.auth.dto.AuthRequest;
import com.v360.gateway.auth.dto.AuthResponse;
import com.v360.gateway.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ClientCredentialsAuthenticationService {

    private final ClientCredentialsProperties properties;
    private final JwtService jwtService;

    public ClientCredentialsAuthenticationService(ClientCredentialsProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    public AuthResponse authenticate(AuthRequest request) {
        ClientCredentialsProperties.ClientConfig client = properties.findClient(request.clientId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS",
                        "Cliente não cadastrado ou credenciais inválidas"
                ));

        if (!client.getClientSecret().equals(request.clientSecret())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "Cliente não cadastrado ou credenciais inválidas"
            );
        }

        String token = jwtService.generateToken(
                client.getClientId(),
                client.getTenantCode(),
                client.getRoles()
        );

        return AuthResponse.bearer(
                token,
                jwtService.getExpirationSeconds(),
                client.getTenantCode(),
                client.getRoles()
        );
    }
}
