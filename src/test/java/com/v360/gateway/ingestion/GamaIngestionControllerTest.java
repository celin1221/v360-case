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
class GamaIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseOrderRepository repository;

    private static final String SAMPLE_GAMA_JSON = """
            [
              {
                "ped": "GL-778",
                "item": 1,
                "cnpj_fornecedor": "34567890000112",
                "nome_fornecedor": "Transportes Ideal ME",
                "dt_criacao": 1786752000,
                "cod_mat": "TRP-01",
                "desc_mat": "Pallet de madeira",
                "um": "CX",
                "fator_conv": 12,
                "qtd_ped": 10,
                "qtd_rec": 2,
                "preco_unit_centavos": 120000,
                "situacao": 1
              },
              {
                "ped": "GL-778",
                "item": 2,
                "cnpj_fornecedor": "34567890000112",
                "nome_fornecedor": "Transportes Ideal ME",
                "dt_criacao": 1786752000,
                "cod_mat": "TRP-09",
                "desc_mat": "Caixa organizadora",
                "um": "CX",
                "fator_conv": 3,
                "qtd_ped": 4,
                "qtd_rec": 0,
                "preco_unit_centavos": 10000,
                "situacao": 1
              },
              {
                "ped": "GL-779",
                "item": 1,
                "cnpj_fornecedor": "56789012000134",
                "nome_fornecedor": "Armazéns Rio Claro Ltda",
                "dt_criacao": 1784160000,
                "cod_mat": "ARM-10",
                "desc_mat": "Estrado metálico",
                "um": "UN",
                "fator_conv": 1,
                "qtd_ped": 100,
                "qtd_rec": 100,
                "preco_unit_centavos": 3500,
                "situacao": 2
              }
            ]
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
    @DisplayName("Deve ingerir com sucesso o payload flat do Cliente Gama quando autenticado como gama-client")
    void shouldIngestGamaSuccessfullyWithGamaClientToken() throws Exception {
        String token = getAccessToken("gama-client", "gama-secret-123");

        mockMvc.perform(post("/api/v1/ingestion/gama")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_GAMA_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("CLI-GAMA-003"))
                .andExpect(jsonPath("$.ordersProcessed").value(2))
                .andExpect(jsonPath("$.orderNumbers[0]").value("GL-778"))
                .andExpect(jsonPath("$.orderNumbers[1]").value("GL-779"))
                .andExpect(jsonPath("$.itemsProcessed").value(3))
                .andExpect(jsonPath("$.message").value("Carga de pedidos processada com sucesso"));

        Optional<PurchaseOrder> orderOpt = repository.findByClientIdAndPoNumber("CLI-GAMA-003", "GL-778");
        assertThat(orderOpt).isPresent();
        PurchaseOrder order = orderOpt.get();
        assertThat(order.getItems()).hasSize(2);

        // Validar item normalizado em base units
        var item1 = order.getItems().get(0);
        assertThat(item1.getUom()).isEqualTo("UN");
        assertThat(item1.getQuantityOrdered()).isEqualByComparingTo("120.0000");
        assertThat(item1.getQuantityReceived()).isEqualByComparingTo("24.0000");
        assertThat(item1.getUnitPrice()).isEqualByComparingTo("100.0000");
        assertThat(item1.getOriginalUom()).isEqualTo("CX");
        assertThat(item1.getOriginalQuantity()).isEqualByComparingTo("10.0000");
        assertThat(item1.getConversionFactor()).isEqualByComparingTo("12.0000");
    }

    @Test
    @DisplayName("Superusuário da plataforma (v360-platform) deve conseguir ingerir carga do Cliente Gama")
    void shouldIngestGamaSuccessfullyWithPlatformToken() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        mockMvc.perform(post("/api/v1/ingestion/gama")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_GAMA_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("CLI-GAMA-003"))
                .andExpect(jsonPath("$.ordersProcessed").value(2));
    }

    @Test
    @DisplayName("Deve rejeitar ingestão do Cliente Gama quando autenticado com credenciais de outro cliente (ex: alfa-client)")
    void shouldRejectIngestionWhenUsingAnotherClientToken() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        mockMvc.perform(post("/api/v1/ingestion/gama")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_GAMA_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_CLIENT_ACCESS"));
    }

    @Test
    @DisplayName("Deve rejeitar requisição não autenticada sem cabeçalho Authorization")
    void shouldRejectIngestionWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/ingestion/gama")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_GAMA_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Ingestão deve ser idempotente: reenvio atualiza pedido sem duplicar")
    void shouldBeIdempotentOnSubsequentIngestions() throws Exception {
        String token = getAccessToken("gama-client", "gama-secret-123");

        mockMvc.perform(post("/api/v1/ingestion/gama")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_GAMA_JSON))
                .andExpect(status().isOk());

        assertThat(repository.count()).isEqualTo(2);

        String updatedJson = """
                [
                  {
                    "ped": "GL-778",
                    "item": 1,
                    "cnpj_fornecedor": "34567890000112",
                    "nome_fornecedor": "Transportes Ideal ME",
                    "dt_criacao": 1786752000,
                    "cod_mat": "TRP-01",
                    "desc_mat": "Pallet de madeira",
                    "um": "CX",
                    "fator_conv": 12,
                    "qtd_ped": 10,
                    "qtd_rec": 10,
                    "preco_unit_centavos": 120000,
                    "situacao": 2
                  }
                ]
                """;

        mockMvc.perform(post("/api/v1/ingestion/gama")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedJson))
                .andExpect(status().isOk());

        assertThat(repository.count()).isEqualTo(2);
        PurchaseOrder updated = repository.findByClientIdAndPoNumber("CLI-GAMA-003", "GL-778").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(updated.getItems().get(0).getQuantityReceived()).isEqualByComparingTo("120.0000");
    }
}
