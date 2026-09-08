package com.v360.gateway.query.service;

import com.v360.gateway.common.exception.ApiException;
import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.Vendor;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import com.v360.gateway.query.dto.PurchaseOrderResponse;
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

    public Page<PurchaseOrderResponse> queryOrders(
            ClientPrincipal principal,
            String clientId,
            String vendorTaxId,
            OrderStatus status,
            Boolean onlyPendingBalance,
            Pageable pageable
    ) {
        validateClientPrincipal(principal);

        String effectiveClientId;
        if (principal.isPlatform()) {
            // Plataforma V360 pode consultar todos os clientes ou filtrar por um específico
            effectiveClientId = (clientId != null && !clientId.isBlank()) ? clientId.trim() : null;
        } else {
            // Cliente comum só pode consultar seus próprios pedidos
            if (clientId != null && !clientId.isBlank() && !clientId.trim().equalsIgnoreCase(principal.tenantCode())) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN_CLIENT_ACCESS",
                        "O cliente autenticado (" + principal.clientId() + ") não tem permissão para consultar pedidos de outros clientes (" + clientId + ")"
                );
            }
            effectiveClientId = principal.tenantCode();
        }

        String normalizedTaxId = (vendorTaxId != null && !vendorTaxId.isBlank())
                ? Vendor.normalizeTaxId(vendorTaxId)
                : null;

        Page<PurchaseOrder> page = repository.findWithFilters(
                effectiveClientId,
                normalizedTaxId,
                status,
                onlyPendingBalance,
                pageable
        );

        return page.map(PurchaseOrderResponse::fromDomain);
    }

    public PurchaseOrderResponse findById(ClientPrincipal principal, Long id) {
        validateClientPrincipal(principal);

        PurchaseOrder order = repository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND",
                        "Pedido de compra não encontrado com ID: " + id
                ));

        validateClientOrderAccess(principal, order);
        return PurchaseOrderResponse.fromDomain(order);
    }

    public PurchaseOrderResponse findByPoNumber(ClientPrincipal principal, String poNumber, String clientId) {
        validateClientPrincipal(principal);

        if (poNumber == null || poNumber.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_PO_NUMBER", "Número do pedido é obrigatório");
        }

        PurchaseOrder order;
        if (principal.isPlatform()) {
            if (clientId != null && !clientId.isBlank()) {
                order = repository.findByClientIdAndPoNumber(clientId.trim(), poNumber.trim())
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.NOT_FOUND,
                                "ORDER_NOT_FOUND",
                                "Pedido de compra não encontrado com número '" + poNumber + "' para o cliente '" + clientId + "'"
                        ));
            } else {
                order = repository.findByPoNumber(poNumber.trim())
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.NOT_FOUND,
                                "ORDER_NOT_FOUND",
                                "Pedido de compra não encontrado com número '" + poNumber + "'"
                        ));
            }
        } else {
            order = repository.findByClientIdAndPoNumber(principal.tenantCode(), poNumber.trim())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "ORDER_NOT_FOUND",
                            "Pedido de compra não encontrado com número '" + poNumber + "'"
                    ));
        }

        return PurchaseOrderResponse.fromDomain(order);
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
