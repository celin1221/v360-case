package com.v360.gateway.reconciliation.controller;

import com.v360.gateway.reconciliation.dto.InvoiceReconciliationRequest;
import com.v360.gateway.reconciliation.dto.ReconciliationReportResponse;
import com.v360.gateway.reconciliation.dto.ReconciliationResponse;
import com.v360.gateway.reconciliation.service.InvoiceReconciliationService;
import com.v360.gateway.security.ClientPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reconciliations")
@Tag(name = "Reconciliation", description = "Motor de reconciliation automatizada de invoices contra purchase orders da plataforma V360")
@SecurityRequirement(name = "BearerAuth")
public class ReconciliationController {

    private final InvoiceReconciliationService reconciliationService;

    public ReconciliationController(InvoiceReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping
    @Operation(
            summary = "Reconciliar invoice de fornecedor contra purchase order",
            description = "Valida a invoice contra os termos do purchase order (vendor, order status, materiais, pending balance a receber e preço unitário com tolerância de até R$ 0,01). Retorna status APPROVED se conforme ou REJECTED com lista detalhada de divergências capturadas por linha e item.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reconciliation processada com sucesso (APPROVED ou REJECTED)",
                            content = @Content(schema = @Schema(implementation = ReconciliationResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Requisição inválida ou parâmetros obrigatórios ausentes"),
                    @ApiResponse(responseCode = "401", description = "Não autenticado"),
                    @ApiResponse(responseCode = "403", description = "Não autorizado para reconciliar purchase orders de outro client")
            }
    )
    public ResponseEntity<ReconciliationResponse> reconcileInvoice(
            @AuthenticationPrincipal ClientPrincipal principal,
            @Valid @RequestBody InvoiceReconciliationRequest request
    ) {
        ReconciliationResponse response = reconciliationService.reconcile(principal, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/report")
    @Operation(
            summary = "Relatório analítico consolidado de reconciliations",
            description = "Consolida métricas operacionais da plataforma: total de notas conferidas, total aprovadas, total rejeitadas, taxa percentual de aprovação, contagem de divergências por tipo e histórico recente de conferências. Permite filtrar por clientId para ROLE_PLATFORM e garante isolamento automático para ROLE_CLIENT.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Relatório analítico gerado com sucesso",
                            content = @Content(schema = @Schema(implementation = ReconciliationReportResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Não autenticado"),
                    @ApiResponse(responseCode = "403", description = "Não autorizado para consultar relatórios de outro client")
            }
    )
    public ResponseEntity<ReconciliationReportResponse> getReport(
            @AuthenticationPrincipal ClientPrincipal principal,
            @Parameter(description = "Identificador do client para filtrar métricas (exclusivo para superusuários ROLE_PLATFORM)")
            @RequestParam(value = "clientId", required = false) String clientId
    ) {
        ReconciliationReportResponse report = reconciliationService.generateReport(principal, clientId);
        return ResponseEntity.ok(report);
    }
}
