package com.v360.gateway.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v360.gateway.auth.dto.AuthRequest;
import com.v360.gateway.auth.dto.AuthResponse;
import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import com.v360.gateway.ingestion.adapter.beta.dto.BetaCsvRawRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BetaIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseOrderRepository repository;

    private static final String SAMPLE_CABECALHO = """
            NUMERO_PEDIDO;FORNECEDOR_CNPJ;FORNECEDOR_RAZAO_SOCIAL;EMISSAO;SITUACAO;MOEDA
            20260088412;12.345.678/0001-90;Distribuidora Horizonte Ltda;15/08/2026;EM ABERTO;BRL
            20260088413;98.765.432/0001-55;Frigorífico Boa Mesa S.A.;01/08/2026;BLOQUEADO;BRL
            """;

    private static final String SAMPLE_ITENS = """
            NUMERO_PEDIDO;ITEM;CODIGO_MATERIAL;DESCRICAO;UNIDADE;QTD_PEDIDA;QTD_RECEBIDA;PRECO_UNITARIO
            20260088412;1;MAT-77;Óleo de soja 900ml;UN;1.200,000;400,000;6,49
            20260088412;2;MAT-78;Açúcar refinado 1kg;UN;500,000;0,000;4,15
            20260088413;1;MAT-91;Carne bovina dianteiro kg;KG;2.000,000;0,000;27,90
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
    @DisplayName("Deve ingerir com sucesso arquivos multipart do Cliente Beta quando autenticado como beta-client")
    void shouldIngestBetaMultipartSuccessfully() throws Exception {
        String token = getAccessToken("beta-client", "beta-secret-123");

        MockMultipartFile headerFile = new MockMultipartFile(
                "headerFile",
                "cabecalho.csv",
                "text/csv",
                SAMPLE_CABECALHO.getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile itemsFile = new MockMultipartFile(
                "itemsFile",
                "itens.csv",
                "text/csv",
                SAMPLE_ITENS.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/ingestion/beta")
                        .file(headerFile)
                        .file(itemsFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("CLI-BETA-002"))
                .andExpect(jsonPath("$.ordersProcessed").value(2))
                .andExpect(jsonPath("$.itemsProcessed").value(3))
                .andExpect(jsonPath("$.orderNumbers[0]").value("20260088412"))
                .andExpect(jsonPath("$.orderNumbers[1]").value("20260088413"));

        Optional<PurchaseOrder> po1Opt = repository.findByClientIdAndPoNumber("CLI-BETA-002", "20260088412");
        assertThat(po1Opt).isPresent();
        PurchaseOrder po1 = po1Opt.get();
        assertThat(po1.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(po1.getVendor().taxId()).isEqualTo("12345678000190");
        assertThat(po1.getItems()).hasSize(2);
        assertThat(po1.getItems().get(0).getQuantityOrdered()).isEqualByComparingTo(new BigDecimal("1200.000"));
        assertThat(po1.getItems().get(0).getPendingQuantity()).isEqualByComparingTo(new BigDecimal("800.000"));

        Optional<PurchaseOrder> po2Opt = repository.findByClientIdAndPoNumber("CLI-BETA-002", "20260088413");
        assertThat(po2Opt).isPresent();
        PurchaseOrder po2 = po2Opt.get();
        assertThat(po2.getStatus()).isEqualTo(OrderStatus.BLOCKED);
        assertThat(po2.getVendor().taxId()).isEqualTo("98765432000155");
        assertThat(po2.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("Deve ingerir com sucesso os dados CSV do Cliente Beta via payload JSON bruto")
    void shouldIngestBetaRawJsonSuccessfully() throws Exception {
        String token = getAccessToken("beta-client", "beta-secret-123");

        BetaCsvRawRequest request = new BetaCsvRawRequest(SAMPLE_CABECALHO, SAMPLE_ITENS);

        mockMvc.perform(post("/api/v1/ingestion/beta")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("CLI-BETA-002"))
                .andExpect(jsonPath("$.ordersProcessed").value(2))
                .andExpect(jsonPath("$.itemsProcessed").value(3));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Deve realizar upsert idempotente ao reenviar carga modificada do Cliente Beta")
    void shouldSupportIdempotentUpsertForBeta() throws Exception {
        String token = getAccessToken("beta-client", "beta-secret-123");

        // 1ª ingestão
        BetaCsvRawRequest req1 = new BetaCsvRawRequest(SAMPLE_CABECALHO, SAMPLE_ITENS);
        mockMvc.perform(post("/api/v1/ingestion/beta")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk());

        assertThat(repository.count()).isEqualTo(2);

        // 2ª ingestão: atualizar situação de 20260088412 para ENCERRADO
        String cabecalhoAtualizado = """
                NUMERO_PEDIDO;FORNECEDOR_CNPJ;FORNECEDOR_RAZAO_SOCIAL;EMISSAO;SITUACAO;MOEDA
                20260088412;12.345.678/0001-90;Distribuidora Horizonte Ltda;15/08/2026;ENCERRADO;BRL
                """;
        String itensAtualizados = """
                NUMERO_PEDIDO;ITEM;CODIGO_MATERIAL;DESCRICAO;UNIDADE;QTD_PEDIDA;QTD_RECEBIDA;PRECO_UNITARIO
                20260088412;1;MAT-77;Óleo de soja 900ml;UN;1.200,000;1.200,000;6,49
                """;

        BetaCsvRawRequest req2 = new BetaCsvRawRequest(cabecalhoAtualizado, itensAtualizados);
        mockMvc.perform(post("/api/v1/ingestion/beta")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk());

        // Continua com 2 pedidos (não duplicou)
        assertThat(repository.count()).isEqualTo(2);

        PurchaseOrder atualizado = repository.findByClientIdAndPoNumber("CLI-BETA-002", "20260088412").orElseThrow();
        assertThat(atualizado.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(atualizado.getItems()).hasSize(1);
        assertThat(atualizado.getItems().getFirst().getQuantityReceived()).isEqualByComparingTo(new BigDecimal("1200.000"));
        assertThat(atualizado.getItems().getFirst().getPendingQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Deve permitir que o superusuário da plataforma V360 envie cargas do Cliente Beta")
    void shouldAllowPlatformRoleToIngestBeta() throws Exception {
        String token = getAccessToken("v360-platform", "platform-secret-123");

        BetaCsvRawRequest request = new BetaCsvRawRequest(SAMPLE_CABECALHO, SAMPLE_ITENS);

        mockMvc.perform(post("/api/v1/ingestion/beta")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("CLI-BETA-002"));
    }

    @Test
    @DisplayName("Deve bloquear com HTTP 403 quando outro cliente tenta enviar carga da Beta")
    void shouldDenyOtherClientsFromIngestingBeta() throws Exception {
        String token = getAccessToken("alfa-client", "alfa-secret-123");

        BetaCsvRawRequest request = new BetaCsvRawRequest(SAMPLE_CABECALHO, SAMPLE_ITENS);

        mockMvc.perform(post("/api/v1/ingestion/beta")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_CLIENT_ACCESS"));
    }

    @Test
    @DisplayName("Deve rejeitar requisição sem token com HTTP 401")
    void shouldDenyUnauthenticatedRequests() throws Exception {
        BetaCsvRawRequest request = new BetaCsvRawRequest(SAMPLE_CABECALHO, SAMPLE_ITENS);

        mockMvc.perform(post("/api/v1/ingestion/beta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Deve rejeitar envio multipart sem arquivo de cabeçalho com HTTP 400")
    void shouldRejectMissingHeaderFile() throws Exception {
        String token = getAccessToken("beta-client", "beta-secret-123");

        MockMultipartFile itemsFile = new MockMultipartFile(
                "itemsFile",
                "itens.csv",
                "text/csv",
                SAMPLE_ITENS.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/ingestion/beta")
                        .file(itemsFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
