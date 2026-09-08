package com.v360.gateway.ingestion.controller;

import com.v360.gateway.common.exception.ApiException;
import com.v360.gateway.ingestion.adapter.beta.BetaCsvAdapter;
import com.v360.gateway.ingestion.adapter.beta.dto.BetaCsvRawRequest;
import com.v360.gateway.ingestion.dto.IngestionResultResponse;
import com.v360.gateway.ingestion.service.BetaIngestionService;
import com.v360.gateway.security.ClientPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/ingestion/beta")
@Tag(name = "Ingestão de Clientes", description = "Endpoints de ingestão e normalização de pedidos de compra dos clientes")
@SecurityRequirement(name = "BearerAuth")
public class BetaIngestionController {

    private final BetaIngestionService ingestionService;

    public BetaIngestionController(BetaIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Ingerir pedidos do Cliente Beta Alimentos via upload multipart",
            description = "Recebe dois arquivos CSV (cabecalho e itens) no formato padrão brasileiro (delimitados por ponto e vírgula), normaliza para o modelo canônico e persiste idempotentemente.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Carga CSV processada com sucesso",
                            content = @Content(schema = @Schema(implementation = IngestionResultResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Arquivos CSV inválidos ou malformados"),
                    @ApiResponse(responseCode = "401", description = "Não autenticado"),
                    @ApiResponse(responseCode = "403", description = "Não autorizado para o cliente autenticado")
            }
    )
    public ResponseEntity<IngestionResultResponse> ingestBetaMultipart(
            @AuthenticationPrincipal ClientPrincipal principal,
            @Parameter(description = "Arquivo CSV com cabeçalhos dos pedidos (cabecalho.csv)")
            @RequestParam("headerFile") MultipartFile headerFile,
            @Parameter(description = "Arquivo CSV com itens dos pedidos (itens.csv)")
            @RequestParam(value = "itemsFile", required = false) MultipartFile itemsFile
    ) {
        validateTenantAccess(principal);

        if (headerFile == null || headerFile.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_HEADER_FILE", "O arquivo de cabeçalho (headerFile) é obrigatório");
        }

        try {
            String headerContent = new String(headerFile.getBytes(), StandardCharsets.UTF_8);
            String itemsContent = (itemsFile != null && !itemsFile.isEmpty())
                    ? new String(itemsFile.getBytes(), StandardCharsets.UTF_8)
                    : "";

            IngestionResultResponse response = ingestionService.ingest(headerContent, itemsContent);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_ERROR", "Falha ao ler arquivos CSV enviados: " + e.getMessage());
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Ingerir pedidos do Cliente Beta Alimentos via texto bruto (JSON)",
            description = "Recebe o conteúdo dos arquivos CSV em formato string em um payload JSON, facilitando testes via API.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Carga processada com sucesso",
                            content = @Content(schema = @Schema(implementation = IngestionResultResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Dados CSV inválidos ou malformados"),
                    @ApiResponse(responseCode = "401", description = "Não autenticado"),
                    @ApiResponse(responseCode = "403", description = "Não autorizado para o cliente autenticado")
            }
    )
    public ResponseEntity<IngestionResultResponse> ingestBetaRaw(
            @AuthenticationPrincipal ClientPrincipal principal,
            @RequestBody BetaCsvRawRequest request
    ) {
        validateTenantAccess(principal);

        if (request == null || request.headerCsv() == null || request.headerCsv().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_HEADER_CONTENT", "O conteúdo do cabeçalho CSV é obrigatório");
        }

        IngestionResultResponse response = ingestionService.ingest(request.headerCsv(), request.itemsCsv());
        return ResponseEntity.ok(response);
    }

    private void validateTenantAccess(ClientPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Cliente não autenticado");
        }

        if (!principal.isPlatform() && !principal.tenantCode().equals(BetaCsvAdapter.BETA_CLIENT_ID)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN_CLIENT_ACCESS",
                    "O cliente autenticado (" + principal.clientId() + ") não tem permissão para enviar cargas do Cliente Beta (" + BetaCsvAdapter.BETA_CLIENT_ID + ")"
            );
        }
    }
}
