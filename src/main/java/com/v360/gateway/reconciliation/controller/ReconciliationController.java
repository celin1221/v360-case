package com.v360.gateway.reconciliation.controller;

import com.v360.gateway.reconciliation.dto.InvoiceReconciliationRequest;
import com.v360.gateway.reconciliation.dto.ReconciliationResponse;
import com.v360.gateway.reconciliation.service.InvoiceReconciliationService;
import com.v360.gateway.security.ClientPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reconciliations")
@Tag(name = "Conferência (Three-Way Matching)", description = "Motor de conferência automatizada de notas fiscais contra pedidos de compra da plataforma V360")
@SecurityRequirement(name = "BearerAuth")
public class ReconciliationController {

    private final InvoiceReconciliationService reconciliationService;

    public ReconciliationController(InvoiceReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping
    @Operation(
            summary = "Conferir nota fiscal contra pedido de compra (Three-Way Matching)",
            description = "Valida a nota fiscal contra os termos do pedido de compra acordado (fornecedor, situação do pedido, materiais, saldo pendente a receber e preço unitário com tolerância de até R$ 0,01). Retorna status APPROVED se conforme ou REJECTED com lista detalhada de divergências capturadas por linha e item.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Conferência realizada com sucesso (aprovada ou rejeitada)",
                            content = @Content(schema = @Schema(implementation = ReconciliationResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Requisição inválida ou parâmetros obrigatórios ausentes"),
                    @ApiResponse(responseCode = "401", description = "Não autenticado"),
                    @ApiResponse(responseCode = "403", description = "Não autorizado para conferir pedidos de outro cliente")
            }
    )
    public ResponseEntity<ReconciliationResponse> reconcileInvoice(
            @AuthenticationPrincipal ClientPrincipal principal,
            @Valid @RequestBody InvoiceReconciliationRequest request
    ) {
        ReconciliationResponse response = reconciliationService.reconcile(principal, request);
        return ResponseEntity.ok(response);
    }
}
