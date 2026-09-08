package com.v360.gateway.reconciliation.dto;

import com.v360.gateway.domain.model.DivergenceType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ReconciliationReportResponse(
        String clientId,
        long totalReconciliations,
        long totalApproved,
        long totalRejected,
        BigDecimal approvalRatePercentage,
        Map<DivergenceType, Long> divergenceCounts,
        List<ReconciliationResponse> recentReconciliations
) {
}
