package com.v360.gateway.query.service;

import com.v360.gateway.common.exception.ApiException;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.port.PurchaseOrderFilter;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import com.v360.gateway.query.dto.PurchaseOrderResponse;
import com.v360.gateway.query.dto.PurchaseOrderSummaryResponse;
import com.v360.gateway.security.ClientPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PurchaseOrderQueryService {

    private final PurchaseOrderRepository repository;

    public PurchaseOrderQueryService(PurchaseOrderRepository repository) {
        this.repository = repository;
    }

    public Page<PurchaseOrderSummaryResponse> queryPurchaseOrders(
            ClientPrincipal principal,
            PurchaseOrderFilter filter,
            Pageable pageable
    ) {
        validateClientPrincipal(principal);

        String requestedClientId = (filter != null) ? filter.clientId() : null;
        String effectiveClientId = resolveEffectiveClientId(principal, requestedClientId, false);

        PurchaseOrderFilter effectiveFilter = new PurchaseOrderFilter(
                effectiveClientId,
                (filter != null) ? filter.vendorTaxId() : null,
                (filter != null) ? filter.status() : null,
                (filter != null) ? filter.onlyPendingBalance() : null
        );

        Page<PurchaseOrder> page = repository.findWithFilters(effectiveFilter, pageable);
        return page.map(PurchaseOrderSummaryResponse::fromDomain);
    }

    public PurchaseOrderResponse findPurchaseOrderById(ClientPrincipal principal, Long id) {
        validateClientPrincipal(principal);

        PurchaseOrder order = repository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PURCHASE_ORDER_NOT_FOUND",
                        "Pedido de compra não encontrado com ID: " + id
                ));

        validateClientOrderAccess(principal, order);
        return PurchaseOrderResponse.fromDomain(order);
    }

    public PurchaseOrderResponse findPurchaseOrderByNumber(ClientPrincipal principal, String poNumber, String clientId) {
        validateClientPrincipal(principal);

        if (poNumber == null || poNumber.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_PURCHASE_ORDER_NUMBER", "Número do pedido de compra é obrigatório");
        }

        // Exige clientId para a plataforma para garantir desambiguação multi-tenant sem riscos de colisão
        String effectiveClientId = resolveEffectiveClientId(principal, clientId, true);

        PurchaseOrder order = repository.findByClientIdAndPoNumber(effectiveClientId, poNumber.trim())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PURCHASE_ORDER_NOT_FOUND",
                        "Pedido de compra não encontrado para o cliente '" + effectiveClientId + "' e número '" + poNumber + "'"
                ));

        return PurchaseOrderResponse.fromDomain(order);
    }

    private String resolveEffectiveClientId(ClientPrincipal principal, String requestedClientId, boolean requireForPlatform) {
        String normalizedClientId = resolveTenantCode(requestedClientId);
        if (principal.isPlatform()) {
            if (requireForPlatform && (normalizedClientId == null || normalizedClientId.isBlank())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "MISSING_CLIENT_ID",
                        "Para desambiguação multi-tenant nesta consulta, a Plataforma V360 deve informar o parâmetro 'clientId'"
                );
            }
            return normalizedClientId;
        }

        // Cliente regular: não pode requisitar outro clientId
        if (normalizedClientId != null && !normalizedClientId.equalsIgnoreCase(principal.tenantCode())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN_CLIENT_ACCESS",
                    "O cliente autenticado (" + principal.clientId() + ") não tem permissão para consultar dados do cliente " + requestedClientId
            );
        }

        return principal.tenantCode();
    }

    private String resolveTenantCode(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        return switch (trimmed.toLowerCase()) {
            case "alfa", "cli-alfa-001" -> "CLI-ALFA-001";
            case "beta", "cli-beta-002" -> "CLI-BETA-002";
            case "gama", "cli-gama-003" -> "CLI-GAMA-003";
            default -> trimmed;
        };
    }

    private void validateClientPrincipal(ClientPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Cliente não autenticado");
        }
    }

    private void validateClientOrderAccess(ClientPrincipal principal, PurchaseOrder order) {
        if (!principal.isPlatform() && !order.getClientId().equalsIgnoreCase(principal.tenantCode())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN_CLIENT_ACCESS",
                    "Acesso não autorizado ao pedido informado"
            );
        }
    }
}
