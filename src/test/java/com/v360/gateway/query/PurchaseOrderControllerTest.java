package com.v360.gateway.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v360.gateway.auth.dto.AuthRequest;
import com.v360.gateway.auth.dto.AuthResponse;
import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.PurchaseOrderItem;
import com.v360.gateway.domain.model.Vendor;
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
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseOrderRepository repository;

    private PurchaseOrder alfaOrder;
    private PurchaseOrder betaOpenOrder;
    private PurchaseOrder betaBlockedOrder;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        // 1. Alfa Order: Aberto, com saldo pendente (100 pedidos, 60 recebidos -> saldo 40)
        alfaOrder = new PurchaseOrder(
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
                new BigDecimal("60"),
                new BigDecimal("45.90")
        ));
        alfaOrder.addItem(new PurchaseOrderItem(
                20,
                "MAT-1002",
                "Perfil U 3m",
                "UN",
                new BigDecimal("50"),
                new BigDecimal("50"), // totalmente recebido
                new BigDecimal("128.75")
        ));
        alfaOrder = repository.save(alfaOrder);

        // 2. Beta Order 1: Aberto, com saldo pendente
        betaOpenOrder = new PurchaseOrder(
                "CLI-BETA-002",
                "20260088412",
                LocalDate.of(2026, 8, 15),
                OrderStatus.OPEN,
                "BRL",
                new Vendor("12345678000190", "Distribuidora Horizonte Ltda")
        );
        betaOpenOrder.addItem(new PurchaseOrderItem(
                1,
                "MAT-77",
                "Óleo de soja 900ml",
                "UN",
                new BigDecimal("1200.000"),
                new BigDecimal("400.000"),
                new BigDecimal("6.49")
        ));
        betaOpenOrder = repository.save(betaOpenOrder);

        // 3. Beta Order 2: Bloqueado
        betaBlockedOrder = new PurchaseOrder(
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
        betaBlockedOrder = repository.save(betaBlockedOrder);
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
    @DisplayName("ROLE_PLATFORM deve consultar todos os pedidos de todos os clientes sem filtros")
    void shouldAllowPlatformRoleToQueryAllOrders() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(3)));
    }

    @Test
    @DisplayName("ROLE_PLATFORM pode filtrar pedidos por clientId específico")
    void shouldFilterByClientIdForPlatformRole() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("clientId", "CLI-ALFA-001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].poNumber").value("4500001234"));
    }

    @Test
    @DisplayName("Cliente comum (ROLE_CLIENT) deve visualizar apenas seus próprios pedidos automaticamente")
    void shouldEnforceClientIsolationForClientRole() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].clientId").value("CLI-ALFA-001"))
                .andExpect(jsonPath("$.content[0].poNumber").value("4500001234"));
    }

    @Test
    @DisplayName("Deve retornar HTTP 403 quando um cliente tenta consultar dados de outro cliente")
    void shouldDenyClientFromQueryingOtherClientOrders() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("clientId", "CLI-BETA-002")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_CLIENT_ACCESS"));
    }

    @Test
    @DisplayName("Deve filtrar por fornecedor com CNPJ limpo ou formatado com máscara")
    void shouldFilterByVendorTaxIdWithAndWithoutMask() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        // 1. Com CNPJ formatado
        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("vendorTaxId", "12.345.678/0001-90")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].poNumber").value("20260088412"));

        // 2. Com CNPJ limpo
        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("vendorTaxId", "12345678000190")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].poNumber").value("20260088412"));
    }

    @Test
    @DisplayName("Deve filtrar pedidos por situação (status)")
    void shouldFilterByStatus() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("status", "BLOCKED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].poNumber").value("20260088413"))
                .andExpect(jsonPath("$.content[0].status").value("BLOCKED"));
    }

    @Test
    @DisplayName("Deve filtrar apenas pedidos com saldo pendente a receber (onlyPendingBalance=true)")
    void shouldFilterByOnlyPendingBalance() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        // Criar um pedido totalmente recebido (sem saldo pendente)
        PurchaseOrder closedOrder = new PurchaseOrder(
                "CLI-ALFA-001",
                "4500009999",
                LocalDate.now(),
                OrderStatus.CLOSED,
                "BRL",
                new Vendor("23456789000101", "Fornecedor")
        );
        closedOrder.addItem(new PurchaseOrderItem(1, "MAT-1", "Item", "UN", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10")));
        repository.save(closedOrder);

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("onlyPendingBalance", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)) // os 3 pedidos do setUp têm saldo pendente; o fechado não entra
                .andExpect(jsonPath("$.content[*].poNumber").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("4500009999"))));
    }

    @Test
    @DisplayName("Deve suportar paginação de resultados")
    void shouldPaginateResults() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("Deve obter detalhe de um pedido por ID com itens e saldos calculados")
    void shouldGetOrderByIdWithDetails() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        mockMvc.perform(get("/api/v1/purchase-orders/" + alfaOrder.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alfaOrder.getId()))
                .andExpect(jsonPath("$.poNumber").value("4500001234"))
                .andExpect(jsonPath("$.clientId").value("CLI-ALFA-001"))
                .andExpect(jsonPath("$.vendor.taxId").value("23456789000101"))
                .andExpect(jsonPath("$.hasPendingBalance").value(true))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].materialCode").value("MAT-1001"))
                .andExpect(jsonPath("$.items[0].quantityOrdered").value(100.0))
                .andExpect(jsonPath("$.items[0].quantityReceived").value(60.0))
                .andExpect(jsonPath("$.items[0].pendingQuantity").value(40.0));
    }

    @Test
    @DisplayName("Deve obter detalhe de um pedido pelo número comercial (poNumber)")
    void shouldGetOrderByPoNumber() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        mockMvc.perform(get("/api/v1/purchase-orders/by-number/4500001234")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poNumber").value("4500001234"))
                .andExpect(jsonPath("$.clientId").value("CLI-ALFA-001"));
    }

    @Test
    @DisplayName("Deve retornar HTTP 403 quando um cliente tenta ver pedido de outro cliente por ID")
    void shouldDenyClientFromViewingOtherClientOrderById() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        mockMvc.perform(get("/api/v1/purchase-orders/" + betaOpenOrder.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_CLIENT_ACCESS"));
    }

    @Test
    @DisplayName("Deve retornar HTTP 404 quando pedido não existir")
    void shouldReturn404WhenOrderNotFound() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        mockMvc.perform(get("/api/v1/purchase-orders/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("Deve rejeitar requisição sem autenticação com HTTP 401")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/purchase-orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
