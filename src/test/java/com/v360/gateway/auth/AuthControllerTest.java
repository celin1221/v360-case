package com.v360.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v360.gateway.auth.dto.AuthRequest;
import com.v360.gateway.auth.dto.AuthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Deve emitir token JWT válido para a plataforma V360")
    void shouldGenerateTokenForPlatformClient() throws Exception {
        AuthRequest request = new AuthRequest("v360-platform", "platform-secret-123");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.tenantCode").value("PLATFORM"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_PLATFORM"));
    }

    @Test
    @DisplayName("Deve emitir token JWT válido para o Cliente Alfa com tenantCode correto")
    void shouldGenerateTokenForAlfaClient() throws Exception {
        AuthRequest request = new AuthRequest("alfa-client", "alfa-secret-123");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tenantCode").value("CLI-ALFA-001"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_CLIENT"));
    }

    @Test
    @DisplayName("Deve rejeitar credenciais incorretas com HTTP 401")
    void shouldRejectInvalidCredentials() throws Exception {
        AuthRequest request = new AuthRequest("v360-platform", "senha-errada");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Deve rejeitar cliente não cadastrado com HTTP 401")
    void shouldRejectUnregisteredClient() throws Exception {
        AuthRequest request = new AuthRequest("cliente-fantasma", "qualquer-senha");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Deve negar acesso a endpoint protegido quando nenhum token é fornecido")
    void shouldDenyAccessToProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve permitir acesso a endpoint protegido quando token JWT válido é fornecido")
    void shouldAllowAccessToProtectedEndpointWithValidToken() throws Exception {
        // 1. Obter token
        AuthRequest authRequest = new AuthRequest("v360-platform", "platform-secret-123");
        MvcResult authResult = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                authResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        assertThat(authResponse.accessToken()).isNotEmpty();

        // 2. Chamar endpoint protegido com o Bearer token
        mockMvc.perform(get("/api/v1/health")
                        .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.authenticatedClient").value("v360-platform"));
    }

    @Test
    @DisplayName("Deve permitir acesso público à documentação Swagger/OpenAPI")
    void shouldAllowPublicAccessToSwaggerAndDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
