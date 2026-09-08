package com.v360.gateway.reconciliation.service;

import com.v360.gateway.common.exception.ApiException;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.ReconciliationRecord;
import com.v360.gateway.domain.model.Vendor;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import com.v360.gateway.domain.port.ReconciliationAuditRepository;
import com.v360.gateway.reconciliation.dto.InvoiceItemRequest;
import com.v360.gateway.reconciliation.dto.InvoiceReconciliationRequest;
import com.v360.gateway.reconciliation.dto.ReconciliationResponse;
import com.v360.gateway.reconciliation.engine.ReconciliationContext;
import com.v360.gateway.reconciliation.engine.ReconciliationRuleChain;
import com.v360.gateway.security.ClientPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceReconciliationService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ReconciliationAuditRepository auditRepository;
    private final ReconciliationRuleChain ruleChain;

    public InvoiceReconciliationService(PurchaseOrderRepository purchaseOrderRepository,
                                      ReconciliationAuditRepository auditRepository,
                                      ReconciliationRuleChain ruleChain) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.auditRepository = auditRepository;
        this.ruleChain = ruleChain;
    }

    @Transactional
    public ReconciliationResponse reconcile(ClientPrincipal principal, InvoiceReconciliationRequest request) {
        String effectiveClientId = resolveEffectiveClientId(principal, request);
        InvoiceReconciliationRequest normalizedRequest = normalizeItemLineNumbers(request);

        Optional<PurchaseOrder> orderOpt = purchaseOrderRepository.findByClientIdAndPoNumber(
                effectiveClientId,
                normalizedRequest.poNumber().trim()
        );

        ReconciliationContext context = new ReconciliationContext(
                effectiveClientId,
                normalizedRequest,
                orderOpt.orElse(null)
        );

        ruleChain.execute(context);

        String cleanVendorTaxId = Vendor.normalizeTaxId(request.vendorTaxId());
        String resolvedInvoiceNumber = (normalizedRequest.invoiceNumber() != null && !normalizedRequest.invoiceNumber().isBlank())
                ? normalizedRequest.invoiceNumber().trim()
                : "INV-" + normalizedRequest.poNumber().trim();

        ReconciliationRecord record = new ReconciliationRecord(
                effectiveClientId,
                normalizedRequest.poNumber().trim(),
                resolvedInvoiceNumber,
                cleanVendorTaxId,
                context.getStatus(),
                Instant.now(),
                context.getDivergences()
        );

        record = auditRepository.save(record);

        return ReconciliationResponse.fromDomain(record);
    }

    private String resolveEffectiveClientId(ClientPrincipal principal, InvoiceReconciliationRequest request) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Credenciais de autenticação não fornecidas");
        }

        if (principal.isPlatform()) {
            if (request.clientId() == null || request.clientId().isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "MISSING_CLIENT_ID",
                        "Superusuários da plataforma devem informar o 'clientId' para reconciliation da invoice"
                );
            }
            return request.clientId().trim();
        }

        String tenantCode = principal.tenantCode();
        if (request.clientId() != null && !request.clientId().isBlank() && !request.clientId().trim().equalsIgnoreCase(tenantCode)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "Acesso não autorizado para reconciliar purchase orders de outro client"
            );
        }

        return tenantCode;
    }

    private InvoiceReconciliationRequest normalizeItemLineNumbers(InvoiceReconciliationRequest request) {
        if (request.items() == null) {
            return request;
        }

        List<InvoiceItemRequest> normalizedItems = new ArrayList<>();
        int seq = 1;
        for (InvoiceItemRequest item : request.items()) {
            Integer lineNumber = item.lineNumber() != null ? item.lineNumber() : seq;
            normalizedItems.add(new InvoiceItemRequest(
                    lineNumber,
                    item.materialCode(),
                    item.quantity(),
                    item.unitPrice(),
                    item.totalPrice()
            ));
            seq++;
        }

        return new InvoiceReconciliationRequest(
                request.clientId(),
                request.invoiceNumber(),
                request.poNumber(),
                request.vendorTaxId(),
                normalizedItems
        );
    }
}
