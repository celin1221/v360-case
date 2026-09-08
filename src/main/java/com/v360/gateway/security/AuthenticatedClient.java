package com.v360.gateway.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

public class AuthenticatedClient implements Authentication {

    private final String clientId;
    private final String tenantCode;
    private final List<SimpleGrantedAuthority> authorities;
    private boolean authenticated = true;

    public AuthenticatedClient(String clientId, String tenantCode, List<String> roles) {
        this.clientId = clientId;
        this.tenantCode = tenantCode;
        this.authorities = roles != null
                ? roles.stream().map(SimpleGrantedAuthority::new).toList()
                : List.of();
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public boolean isPlatform() {
        return authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM"));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return tenantCode;
    }

    @Override
    public Object getPrincipal() {
        return clientId;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return clientId;
    }
}
