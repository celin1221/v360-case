package com.v360.gateway.query.controller;

import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.query.dto.PurchaseOrderResponse;
import com.v360.gateway.query.service.PurchaseOrderQueryService;
import com.v360.gateway.security.ClientPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@Tag(name = "Consulta de Pedidos", description = "Endpoints canônicos de consulta de pedidos de compra da plataforma V360")
@SecurityRequirement(name = "BearerAuth")
public class PurchaseOrderController {

    private final PurchaseOrderQueryService queryService;

    public PurchaseOrderController(PurchaseOrderQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(
            summary = "Consultar pedidos de compra unificados com filtros e paginação",
            description = "Retorna pedidos normalizados no modelo canônico V360. Suporta filtros por cliente (clientId), fornecedor (CNPJ limpo ou com máscara), situação do pedido (status) e saldo pendente a receber (onlyPendingBalance).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Página de pedidos encontrada com sucesso"),
                    @ApiResponse(responseCode = "401", description = "Não autenticado"),
                    @ApiResponse(responseCode = "403", description = "Não autorizado para consultar outros clientes")
            }
    )
    public ResponseEntity<Page<PurchaseOrderResponse>> listOrders(
            @AuthenticationPrincipal ClientPrincipal principal,
            @Parameter(description = "Identificador imutável do cliente (ex: CLI-ALFA-001, CLI-BETA-002)")
            @RequestParam(value = "clientId", required = false) String clientId,
            @Parameter(description = "CNPJ do fornecedor (com ou sem pontuação)")
            @RequestParam(value = "vendorTaxId", required = false) String vendorTaxId,
            @Parameter(description = "Situação do pedido: OPEN, CLOSED, BLOCKED")
            @RequestParam(value = "status", required = false) OrderStatus status,
            @Parameter(description = "Se true, filtra apenas pedidos onde ao menos um item tem saldo pendente a receber (pendingQuantity > 0)")
            @RequestParam(value = "onlyPendingBalance", required = false) Boolean onlyPendingBalance,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<PurchaseOrderResponse> page = queryService.queryOrders(
                principal,
                clientId,
                vendorTaxId,
                status,
                onlyPendingBalance,
                pageable
        );
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Obter detalhe de um pedido de compra por ID",
            description = "Retorna o cabeçalho e todos os itens do pedido com o saldo pendente detalhado.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Pedido encontrado com sucesso",
                            content = @Content(schema = @Schema(implementation = PurchaseOrderResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Não autenticado"),
                    @ApiResponse(responseCode = "403", description = "Não autorizado para acessar pedido de outro cliente"),
                    @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
            }
    )
    public ResponseEntity<PurchaseOrderResponse> getOrderById(
            @AuthenticationPrincipal ClientPrincipal principal,
            @PathVariable("id") Long id
    ) {
        PurchaseOrderResponse response = queryService.findById(principal, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-number/{poNumber}")
    @Operation(
            summary = "Obter detalhe de um pedido de compra pelo número do pedido",
            description = "Localiza o pedido pelo número comercial de compra (poNumber).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Pedido encontrado com sucesso",
                            content = @Content(schema = @Schema(implementation = PurchaseOrderResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Não autenticado"),
                    @ApiResponse(responseCode = "403", description = "Não autorizado para acessar pedido de outro cliente"),
                    @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
            }
    )
    public ResponseEntity<PurchaseOrderResponse> getOrderByPoNumber(
            @AuthenticationPrincipal ClientPrincipal principal,
            @PathVariable("poNumber") String poNumber,
            @RequestParam(value = "clientId", required = false) String clientId
    ) {
        PurchaseOrderResponse response = queryService.findByPoNumber(principal, poNumber, clientId);
        return ResponseEntity.ok(response);
    }
}
