package com.v360.gateway.reconciliation.dto;

import com.v360.gateway.domain.model.ReconciliationRecord;
import com.v360.gateway.domain.model.ReconciliationStatus;

import java.time.Instant;
import java.util.List;

public record ReconciliationResponse(
        Long id,
        Instant reconciledAt,
        String clientId,
        String poNumber,
        String invoiceNumber,
        String vendorTaxId,
        ReconciliationStatus status,
        List<DivergenceResponse> divergences
) {
    public static ReconciliationResponse fromDomain(ReconciliationRecord record) {
        List<DivergenceResponse> divergenceResponses = record.getDivergences().stream()
                .map(DivergenceResponse::fromDomain)
                .toList();

        return new ReconciliationResponse(
                record.getId(),
                record.getReconciledAt(),
                record.getClientId(),
                record.getPoNumber(),
                record.getInvoiceNumber(),
                record.getVendorTaxId(),
                record.getStatus(),
                divergenceResponses
        );
    }
}
