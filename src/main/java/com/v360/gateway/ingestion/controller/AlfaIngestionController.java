package com.v360.gateway.ingestion.controller;

import com.v360.gateway.common.exception.ApiException;
import com.v360.gateway.ingestion.adapter.alfa.AlfaJsonAdapter;
import com.v360.gateway.ingestion.dto.IngestionResultResponse;
import com.v360.gateway.ingestion.service.AlfaIngestionService;
import com.v360.gateway.security.ClientPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingestion/alfa")
@Tag(name = "Ingestão de Clientes", description = "Endpoints de ingestão e normalização de pedidos de compra dos clientes")
@SecurityRequirement(name = "BearerAuth")
public class AlfaIngestionController {

    private final AlfaIngestionService ingestionService;

    public AlfaIngestionController(AlfaIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Ingerir pedidos de compra do Cliente Alfa Energia",
            description = "Recebe o JSON de pedidos com itens aninhados no formato nativo do Cliente Alfa, normaliza para o modelo canônico e persiste idempotentemente.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Carga processada com sucesso",
                            content = @Content(schema = @Schema(implementation = IngestionResultResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Payload JSON inválido ou malformado"),
                    @ApiResponse(responseCode = "401", description = "Não autenticado"),
                    @ApiResponse(responseCode = "403", description = "Não autorizado para o cliente autenticado")
            }
    )
    public ResponseEntity<IngestionResultResponse> ingestAlfa(
            @AuthenticationPrincipal ClientPrincipal principal,
            @RequestBody String jsonPayload
    ) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Cliente não autenticado");
        }

        if (!principal.isPlatform() && !principal.tenantCode().equals(AlfaJsonAdapter.ALFA_CLIENT_ID)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN_CLIENT_ACCESS",
                    "O cliente autenticado (" + principal.clientId() + ") não tem permissão para enviar cargas do Cliente Alfa (" + AlfaJsonAdapter.ALFA_CLIENT_ID + ")"
            );
        }

        IngestionResultResponse response = ingestionService.ingest(jsonPayload);
        return ResponseEntity.ok(response);
    }
}
