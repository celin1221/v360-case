package com.v360.gateway.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v360.gateway.auth.dto.AuthRequest;
import com.v360.gateway.auth.dto.AuthResponse;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import com.v360.gateway.domain.port.ReconciliationAuditRepository;
import com.v360.gateway.ingestion.service.GamaIngestionService;
import com.v360.gateway.reconciliation.dto.InvoiceItemRequest;
import com.v360.gateway.reconciliation.dto.InvoiceReconciliationRequest;
import com.v360.gateway.domain.model.DivergenceType;
import com.v360.gateway.domain.model.ReconciliationStatus;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("GamaReconciliationIntegrationTest - Three-Way Matching com conversão de unidades (ADR-0003)")
class GamaReconciliationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ReconciliationAuditRepository auditRepository;

    @Autowired
    private GamaIngestionService gamaIngestionService;

    private static final String GAMA_SAMPLE_JSON = """
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
              }
            ]
            """;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        purchaseOrderRepository.deleteAll();
        // Ingestão do pedido GL-778:
        // 10 CX com fator 12 = 120 UN pedidas, 2 CX * 12 = 24 UN recebidas -> Saldo pendente: 96 UN.
        // Preço: 120000 centavos / 100 = R$ 1.200,00 por CX -> R$ 100,00 por UN.
        gamaIngestionService.ingest(GAMA_SAMPLE_JSON);
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
    @DisplayName("Deve aprovar nota fiscal faturada na unidade base (UN) contra pedido comprado em caixas (CX)")
    void shouldApproveInvoiceInBaseUnitsAgainstBoxPurchaseOrder() throws Exception {
        String token = getAccessToken("gama-client", "gama-secret-123");

        // NF do fornecedor emitida em unidades base (UN):
        // 50 pallets (dentro do saldo de 96 UN) ao preço unitário de R$ 100,00
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-GAMA-003",
                "NF-GAMA-501",
                "GL-778",
                "34.567.890/0001-12",
                List.of(new InvoiceItemRequest(1, "TRP-01", new BigDecimal("50.0"), new BigDecimal("100.00"), null))
        );

        mockMvc.perform(post("/api/v1/reconciliations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ReconciliationStatus.APPROVED.name()))
                .andExpect(jsonPath("$.divergences").isEmpty())
                .andExpect(jsonPath("$.invoiceNumber").value("NF-GAMA-501"));
    }

    @Test
    @DisplayName("Deve reprovar nota fiscal se fornecedor faturar com preço da caixa ao invés do preço unitário base")
    void shouldRejectInvoiceWhenPriceMismatchOccursDueToUnconvertedPrice() throws Exception {
        String token = getAccessToken("gama-client", "gama-secret-123");

        // Fornecedor faturou a R$ 1.200,00 a unidade (preço da caixa em vez do preço unitário normalizado de R$ 100,00)
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-GAMA-003",
                "NF-GAMA-502",
                "GL-778",
                "34567890000112",
                List.of(new InvoiceItemRequest(1, "TRP-01", new BigDecimal("10.0"), new BigDecimal("1200.00"), null))
        );

        mockMvc.perform(post("/api/v1/reconciliations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ReconciliationStatus.REJECTED.name()))
                .andExpect(jsonPath("$.divergences[0].code").value(DivergenceType.PRICE_MISMATCH.name()))
                .andExpect(jsonPath("$.divergences[0].materialCode").value("TRP-01"));
    }

    @Test
    @DisplayName("Deve reprovar nota fiscal se quantidade em unidades ultrapassar o saldo pendente normalizado")
    void shouldRejectInvoiceWhenQuantityExceedsPendingBalanceInBaseUnits() throws Exception {
        String token = getAccessToken("gama-client", "gama-secret-123");

        // Saldo pendente é 96 UN (120 pedidas - 24 recebidas). NF cobrando 100 UN:
        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-GAMA-003",
                "NF-GAMA-503",
                "GL-778",
                "34567890000112",
                List.of(new InvoiceItemRequest(1, "TRP-01", new BigDecimal("100.0"), new BigDecimal("100.00"), null))
        );

        mockMvc.perform(post("/api/v1/reconciliations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ReconciliationStatus.REJECTED.name()))
                .andExpect(jsonPath("$.divergences[0].code").value(DivergenceType.QUANTITY_EXCEEDS_PENDING_BALANCE.name()))
                .andExpect(jsonPath("$.divergences[0].expectedValue").value("96"))
                .andExpect(jsonPath("$.divergences[0].actualValue").value("100"));
    }
}
