package com.v360.gateway.reconciliation.engine;

import com.v360.gateway.domain.model.DivergenceType;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.ReconciliationDivergence;
import com.v360.gateway.domain.model.ReconciliationStatus;
import com.v360.gateway.reconciliation.dto.InvoiceReconciliationRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReconciliationContext {

    private final String effectiveClientId;
    private final InvoiceReconciliationRequest request;
    private final PurchaseOrder purchaseOrder;
    private final List<ReconciliationDivergence> divergences = new ArrayList<>();

    public ReconciliationContext(String effectiveClientId, InvoiceReconciliationRequest request, PurchaseOrder purchaseOrder) {
        this.effectiveClientId = effectiveClientId;
        this.request = request;
        this.purchaseOrder = purchaseOrder;
    }

    public void addDivergence(ReconciliationDivergence divergence) {
        this.divergences.add(divergence);
    }

    public void addDivergence(DivergenceType code, Integer lineNumber, String materialCode,
                           String description, String expectedValue, String actualValue, String difference) {
        this.divergences.add(new ReconciliationDivergence(code, lineNumber, materialCode, description, expectedValue, actualValue, difference));
    }

    public boolean hasDivergences() {
        return !divergences.isEmpty();
    }

    public ReconciliationStatus getStatus() {
        return divergences.isEmpty() ? ReconciliationStatus.APPROVED : ReconciliationStatus.REJECTED;
    }

    public String getEffectiveClientId() {
        return effectiveClientId;
    }

    public InvoiceReconciliationRequest getRequest() {
        return request;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public List<ReconciliationDivergence> getDivergences() {
        return Collections.unmodifiableList(divergences);
    }
}
