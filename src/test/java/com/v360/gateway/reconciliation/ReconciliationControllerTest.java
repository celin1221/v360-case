package com.v360.gateway.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v360.gateway.auth.dto.AuthRequest;
import com.v360.gateway.auth.dto.AuthResponse;
import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.PurchaseOrderItem;
import com.v360.gateway.domain.model.Vendor;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import com.v360.gateway.domain.port.ReconciliationAuditRepository;
import com.v360.gateway.reconciliation.dto.InvoiceItemRequest;
import com.v360.gateway.reconciliation.dto.InvoiceReconciliationRequest;
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
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ReconciliationAuditRepository auditRepository;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        purchaseOrderRepository.deleteAll();

        // Cadastra pedido do Cliente Alfa
        PurchaseOrder alfaOrder = new PurchaseOrder(
                "CLI-ALFA-001",
                "4500001234",
                LocalDate.of(2026, 8, 5),
                OrderStatus.OPEN,
                "BRL",
                new Vendor("23456789000101", "Metalúrgica São Jorge S.A.")
        );
        alfaOrder.addItem(new PurchaseOrderItem(
                10,
                "MAT-1001",
                "Chapa de aço 2mm",
                "UN",
                new BigDecimal("100"),
                new BigDecimal("60"), // saldo pendente: 40
                new BigDecimal("45.90")
        ));
        alfaOrder.addItem(new PurchaseOrderItem(
                20,
                "MAT-1002",
                "Perfil U 3m",
                "UN",
                new BigDecimal("50"),
                new BigDecimal("0"), // saldo pendente: 50
                new BigDecimal("128.75")
        ));
        purchaseOrderRepository.save(alfaOrder);

        // Cadastra pedido bloqueado do Cliente Beta
        PurchaseOrder betaBlockedOrder = new PurchaseOrder(
                "CLI-BETA-002",
                "20260088413",
                LocalDate.of(2026, 8, 1),
                OrderStatus.BLOCKED,
                "BRL",
                new Vendor("98765432000155", "Frigorífico Boa Mesa S.A.")
        );
        betaBlockedOrder.addItem(new PurchaseOrderItem(
                1,
                "MAT-91",
                "Carne bovina dianteiro kg",
                "KG",
                new BigDecimal("2000.000"),
                new BigDecimal("0.000"),
                new BigDecimal("27.90")
        ));
        purchaseOrderRepository.save(betaBlockedOrder);
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
    @DisplayName("ROLE_PLATFORM deve conferir com sucesso (APPROVED) uma nota 100% conforme")
    void shouldApproveConformingInvoice() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-2026-001",
                "4500001234",
                "23.456.789/0001-01",
                List.of(
                        new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("40.00"), new BigDecimal("45.90"), null),
                        new InvoiceItemRequest(2, "MAT-1002", new BigDecimal("10.00"), new BigDecimal("128.75"), null)
                )
        );

        mockMvc.perform(post("/api/v1/reconciliations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.poNumber").value("4500001234"))
                .andExpect(jsonPath("$.invoiceNumber").value("NF-2026-001"))
                .andExpect(jsonPath("$.divergences", hasSize(0)));
    }

    @Test
    @DisplayName("Deve aprovar nota com arredondamento de centavo dentro da tolerância de até R$ 0,01")
    void shouldApproveInvoiceWithinPriceTolerance() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-2026-002",
                "4500001234",
                "23456789000101",
                List.of(
                        new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10.00"), new BigDecimal("45.91"), null)
                )
        );

        mockMvc.perform(post("/api/v1/reconciliations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.divergences", hasSize(0)));
    }

    @Test
    @DisplayName("Deve rejeitar (REJECTED) e relatar linha, material e diferença em divergências")
    void shouldRejectAndProvideDetailedItemFeedback() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-2026-003",
                "4500001234",
                "23456789000101",
                List.of(
                        // Excede saldo pendente de 40 em +10
                        new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("50.00"), new BigDecimal("45.90"), null),
                        // Preço unitário diverge além da tolerância (140.00 vs 128.75)
                        new InvoiceItemRequest(2, "MAT-1002", new BigDecimal("5.00"), new BigDecimal("140.00"), null),
                        // Material não consta no pedido
                        new InvoiceItemRequest(3, "MAT-INEXISTENTE", new BigDecimal("2.00"), new BigDecimal("99.00"), null)
                )
        );

        mockMvc.perform(post("/api/v1/reconciliations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.divergences", hasSize(3)))
                .andExpect(jsonPath("$.divergences[?(@.code == 'QUANTITY_EXCEEDS_PENDING_BALANCE')].lineNumber").value(1))
                .andExpect(jsonPath("$.divergences[?(@.code == 'QUANTITY_EXCEEDS_PENDING_BALANCE')].materialCode").value("MAT-1001"))
                .andExpect(jsonPath("$.divergences[?(@.code == 'QUANTITY_EXCEEDS_PENDING_BALANCE')].difference").value("+10"))
                .andExpect(jsonPath("$.divergences[?(@.code == 'PRICE_MISMATCH')].lineNumber").value(2))
                .andExpect(jsonPath("$.divergences[?(@.code == 'PRICE_MISMATCH')].materialCode").value("MAT-1002"))
                .andExpect(jsonPath("$.divergences[?(@.code == 'PRICE_MISMATCH')].difference").value("+11.25"))
                .andExpect(jsonPath("$.divergences[?(@.code == 'ITEM_NOT_FOUND')].lineNumber").value(3))
                .andExpect(jsonPath("$.divergences[?(@.code == 'ITEM_NOT_FOUND')].materialCode").value("MAT-INEXISTENTE"));
    }

    @Test
    @DisplayName("Deve rejeitar com ORDER_NOT_FOUND quando pedido não existir")
    void shouldRejectWhenOrderNotFound() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-2026-999",
                "PEDIDO-FANTASMA",
                "23456789000101",
                List.of(new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10"), new BigDecimal("45.90"), null))
        );

        mockMvc.perform(post("/api/v1/reconciliations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.divergences", hasSize(1)))
                .andExpect(jsonPath("$.divergences[0].code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("ROLE_CLIENT deve conseguir conferir notas de seus próprios pedidos")
    void shouldAllowClientRoleOnOwnOrders() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                null, // default tenantCode CLI-ALFA-001
                "NF-ALFA-001",
                "4500001234",
                "23456789000101",
                List.of(new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10"), new BigDecimal("45.90"), null))
        );

        mockMvc.perform(post("/api/v1/reconciliations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.clientId").value("CLI-ALFA-001"));
    }

    @Test
    @DisplayName("ROLE_CLIENT tentando conferir pedidos de outro cliente deve receber 403 Forbidden")
    void shouldBlockCrossTenantReconciliationForClientRole() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                "CLI-BETA-002", // Cross-tenant
                "NF-BETA-001",
                "20260088413",
                "98765432000155",
                List.of(new InvoiceItemRequest(1, "MAT-91", new BigDecimal("10"), new BigDecimal("27.90"), null))
        );

        mockMvc.perform(post("/api/v1/reconciliations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("ROLE_PLATFORM sem clientId na requisição deve receber 400 Bad Request")
    void shouldRequireClientIdForPlatformRole() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        InvoiceReconciliationRequest request = new InvoiceReconciliationRequest(
                null,
                "NF-100",
                "4500001234",
                "23456789000101",
                List.of(new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10"), new BigDecimal("45.90"), null))
        );

        mockMvc.perform(post("/api/v1/reconciliations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_CLIENT_ID"));
    }

    @Test
    @DisplayName("ROLE_PLATFORM deve obter relatório analítico consolidado via GET /api/v1/reconciliations/report")
    void shouldGenerateAnalyticalReportForPlatformRole() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        // 1. Submete nota aprovada
        InvoiceReconciliationRequest approvedReq = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-REP-001",
                "4500001234",
                "23456789000101",
                List.of(new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10.00"), new BigDecimal("45.90"), null))
        );
        mockMvc.perform(post("/api/v1/reconciliations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approvedReq)))
                .andExpect(status().isOk());

        // 2. Submete nota rejeitada (preço acima do permitido)
        InvoiceReconciliationRequest rejectedReq = new InvoiceReconciliationRequest(
                "CLI-ALFA-001",
                "NF-REP-002",
                "4500001234",
                "23456789000101",
                List.of(new InvoiceItemRequest(1, "MAT-1001", new BigDecimal("10.00"), new BigDecimal("55.00"), null))
        );
        mockMvc.perform(post("/api/v1/reconciliations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectedReq)))
                .andExpect(status().isOk());

        // 3. Consulta relatório global da plataforma
        mockMvc.perform(get("/api/v1/reconciliations/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReconciliations").value(2))
                .andExpect(jsonPath("$.totalApproved").value(1))
                .andExpect(jsonPath("$.totalRejected").value(1))
                .andExpect(jsonPath("$.approvalRatePercentage").value(50.0))
                .andExpect(jsonPath("$.divergenceCounts.PRICE_MISMATCH").value(1))
                .andExpect(jsonPath("$.recentReconciliations", hasSize(2)));
    }

    @Test
    @DisplayName("ROLE_PLATFORM pode filtrar relatório analítico por clientId específico")
    void shouldFilterAnalyticalReportByClientIdForPlatform() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        mockMvc.perform(get("/api/v1/reconciliations/report")
                        .param("clientId", "CLI-ALFA-001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("CLI-ALFA-001"));
    }

    @Test
    @DisplayName("ROLE_CLIENT deve visualizar automaticamente relatório apenas do seu próprio tenant")
    void shouldEnforceClientIsolationOnAnalyticalReport() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        mockMvc.perform(get("/api/v1/reconciliations/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("CLI-ALFA-001"));
    }

    @Test
    @DisplayName("ROLE_CLIENT tentando filtrar relatório de outro cliente deve receber 403 Forbidden")
    void shouldBlockCrossTenantReportAccessForClientRole() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        mockMvc.perform(get("/api/v1/reconciliations/report")
                        .param("clientId", "CLI-BETA-002")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
