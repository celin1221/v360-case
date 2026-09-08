package com.v360.gateway.reconciliation.engine.rules;

import com.v360.gateway.domain.model.DivergenceType;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.reconciliation.dto.InvoiceItemRequest;
import com.v360.gateway.reconciliation.engine.ReconciliationContext;
import com.v360.gateway.reconciliation.engine.ReconciliationRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class ItemExistenceRule implements ReconciliationRule {

    @Override
    public boolean evaluate(ReconciliationContext context) {
        PurchaseOrder order = context.getPurchaseOrder();
        if (order == null || context.getRequest().items() == null) {
            return true;
        }

        for (InvoiceItemRequest item : context.getRequest().items()) {
            boolean exists = order.getItems().stream()
                    .anyMatch(oi -> oi.getMaterialCode() != null &&
                            oi.getMaterialCode().trim().equalsIgnoreCase(item.materialCode().trim()));

            if (!exists) {
                int line = item.lineNumber() != null ? item.lineNumber() : 0;
                context.addDivergence(
                        DivergenceType.ITEM_NOT_FOUND,
                        line > 0 ? line : null,
                        item.materialCode(),
                        "Linha " + (line > 0 ? line : "?") + ": Material '" + item.materialCode() + "' não consta no pedido de compra",
                        "Material cadastrado no pedido",
                        item.materialCode(),
                        null
                );
            }
        }

        return true;
    }
}
