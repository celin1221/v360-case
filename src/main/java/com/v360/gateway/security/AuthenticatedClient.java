package com.v360.gateway.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class AuthenticatedClient extends AbstractAuthenticationToken {

    private final ClientPrincipal principal;

    public AuthenticatedClient(ClientPrincipal principal) {
        super(principal.roles() != null
                ? principal.roles().stream().map(SimpleGrantedAuthority::new).toList()
                : List.of());
        this.principal = principal;
        setAuthenticated(true);
    }

    public String getTenantCode() {
        return principal.tenantCode();
    }

    public boolean isPlatform() {
        return principal.isPlatform();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public ClientPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.clientId();
    }
}
