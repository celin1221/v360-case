package com.v360.gateway.security;

import java.util.List;

public record ClientPrincipal(
        String clientId,
        String tenantCode,
        List<String> roles
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isPlatform() {
        return hasRole("ROLE_PLATFORM");
    }
}
