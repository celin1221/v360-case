package com.v360.gateway.reconciliation.engine.rules;

import com.v360.gateway.domain.model.DivergenceType;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.reconciliation.engine.ReconciliationContext;
import com.v360.gateway.reconciliation.engine.ReconciliationRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class VendorMatchRule implements ReconciliationRule {

    @Override
    public boolean evaluate(ReconciliationContext context) {
        PurchaseOrder order = context.getPurchaseOrder();
        if (order == null || order.getVendor() == null) {
            return true;
        }

        String invoiceTaxId = cleanTaxId(context.getRequest().vendorTaxId());
        String orderTaxId = cleanTaxId(order.getVendor().taxId());

        if (!orderTaxId.equals(invoiceTaxId)) {
            context.addDivergence(
                    DivergenceType.VENDOR_MISMATCH,
                    null,
                    null,
                    "CNPJ do emitente da nota fiscal ('" + invoiceTaxId + "') não confere com o fornecedor acordado no pedido ('" + orderTaxId + "')",
                    orderTaxId,
                    invoiceTaxId,
                    null
            );
        }

        return true;
    }

    private String cleanTaxId(String taxId) {
        return taxId != null ? taxId.replaceAll("\\D", "") : "";
    }
}
