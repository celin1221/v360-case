package com.v360.gateway.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v360.gateway.auth.dto.AuthRequest;
import com.v360.gateway.auth.dto.AuthResponse;
import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlfaIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseOrderRepository repository;

    private static final String SAMPLE_ALFA_JSON = """
            {
              "purchase_orders": [
                {
                  "po_number": "4500001234",
                  "created_at": "2026-08-05",
                  "status": "open",
                  "currency": "BRL",
                  "vendor": {
                    "tax_id": "23456789000101",
                    "name": "Metalúrgica São Jorge S.A."
                  },
                  "items": [
                    {
                      "line": 10,
                      "material": "MAT-1001",
                      "description": "Chapa de aço 2mm",
                      "uom": "UN",
                      "quantity_ordered": 100,
                      "quantity_received": 60,
                      "unit_price": 45.9
                    },
                    {
                      "line": 20,
                      "material": "MAT-1002",
                      "description": "Perfil U 3m",
                      "uom": "UN",
                      "quantity_ordered": 50,
                      "quantity_received": 0,
                      "unit_price": 128.75
                    }
                  ]
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    private String getAccessToken(String clientId, String clientSecret) throws Exception {
        AuthRequest authRequest = new AuthRequest(clientId, clientSecret);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthResponse.class
        );
        return authResponse.accessToken();
    }

    @Test
    @DisplayName("Deve ingerir com sucesso o payload de exemplo do Cliente Alfa quando autenticado como alfa-client")
    void shouldIngestAlfaPayloadSuccessfullyWhenAuthenticatedAsAlfaClient() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        mockMvc.perform(post("/api/v1/ingestion/alfa")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_ALFA_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("CLI-ALFA-001"))
                .andExpect(jsonPath("$.ordersProcessed").value(1))
                .andExpect(jsonPath("$.itemsProcessed").value(2))
                .andExpect(jsonPath("$.orderNumbers[0]").value("4500001234"));

        Optional<PurchaseOrder> savedOpt = repository.findByClientIdAndPoNumber("CLI-ALFA-001", "4500001234");
        assertThat(savedOpt).isPresent();

        PurchaseOrder saved = savedOpt.get();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(saved.getVendor().taxId()).isEqualTo("23456789000101");
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getItems().get(0).getPendingQuantity()).isEqualByComparingTo(new BigDecimal("40"));
        assertThat(saved.getItems().get(1).getPendingQuantity()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    @DisplayName("Deve realizar upsert idempotente ao reenviar carga modificada do mesmo pedido")
    void shouldSupportIdempotentUpsertWhenResubmittingModifiedOrder() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        // 1ª ingestão
        mockMvc.perform(post("/api/v1/ingestion/alfa")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_ALFA_JSON))
                .andExpect(status().isOk());

        assertThat(repository.count()).isEqualTo(1);

        // 2ª ingestão: mesmo número de pedido, porém com status "closed"
        String updatedJson = """
                {
                  "purchase_orders": [
                    {
                      "po_number": "4500001234",
                      "created_at": "2026-08-05",
                      "status": "closed",
                      "currency": "BRL",
                      "vendor": { "tax_id": "23456789000101", "name": "Metalúrgica São Jorge S.A." },
                      "items": [
                        {
                          "line": 10,
                          "material": "MAT-1001",
                          "description": "Chapa de aço 2mm",
                          "uom": "UN",
                          "quantity_ordered": 100,
                          "quantity_received": 100,
                          "unit_price": 45.9
                        }
                      ]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/ingestion/alfa")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedJson))
                .andExpect(status().isOk());

        // Confirma que NÃO duplicou o pedido na base
        assertThat(repository.count()).isEqualTo(1);

        PurchaseOrder updated = repository.findByClientIdAndPoNumber("CLI-ALFA-001", "4500001234").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getItems().getFirst().getQuantityReceived()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(updated.getItems().getFirst().getPendingQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Deve permitir que o superusuário da plataforma V360 envie cargas do Cliente Alfa")
    void shouldAllowPlatformRoleToIngestAlfaPayload() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        mockMvc.perform(post("/api/v1/ingestion/alfa")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_ALFA_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("CLI-ALFA-001"));
    }

    @Test
    @DisplayName("Deve bloquear com HTTP 403 quando outro cliente tenta enviar carga da Alfa")
    void shouldDenyOtherClientsFromIngestingAlfaPayload() throws Exception {
        String token = getAccessToken("beta-client", "beta-secret-123");

        mockMvc.perform(post("/api/v1/ingestion/alfa")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_ALFA_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_CLIENT_ACCESS"));
    }

    @Test
    @DisplayName("Deve rejeitar requisição sem token com HTTP 401")
    void shouldDenyUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/ingestion/alfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_ALFA_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Deve rejeitar JSON malformado com HTTP 400")
    void shouldRejectMalformedJson() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        mockMvc.perform(post("/api/v1/ingestion/alfa")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ json invalido: true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_ALFA_PAYLOAD"));
    }
}
