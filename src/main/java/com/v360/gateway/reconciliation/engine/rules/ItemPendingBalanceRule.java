package com.v360.gateway.reconciliation.engine.rules;

import com.v360.gateway.domain.model.DivergenceType;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.PurchaseOrderItem;
import com.v360.gateway.reconciliation.dto.InvoiceItemRequest;
import com.v360.gateway.reconciliation.engine.ReconciliationContext;
import com.v360.gateway.reconciliation.engine.ReconciliationRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@Order(50)
public class ItemPendingBalanceRule implements ReconciliationRule {

    @Override
    public boolean evaluate(ReconciliationContext context) {
        PurchaseOrder order = context.getPurchaseOrder();
        if (order == null || context.getRequest().items() == null) {
            return true;
        }

        for (InvoiceItemRequest item : context.getRequest().items()) {
            Optional<PurchaseOrderItem> matchingOrderItem = order.getItems().stream()
                    .filter(oi -> oi.getMaterialCode() != null &&
                            oi.getMaterialCode().trim().equalsIgnoreCase(item.materialCode().trim()))
                    .findFirst();

            if (matchingOrderItem.isPresent()) {
                PurchaseOrderItem orderItem = matchingOrderItem.get();
                BigDecimal pendingQuantity = orderItem.getPendingQuantity();
                BigDecimal invoicedQuantity = item.quantity();

                if (invoicedQuantity != null && invoicedQuantity.compareTo(pendingQuantity) > 0) {
                    BigDecimal excess = invoicedQuantity.subtract(pendingQuantity);
                    int line = item.lineNumber() != null ? item.lineNumber() : 0;
                    context.addDivergence(
                            DivergenceType.QUANTITY_EXCEEDS_PENDING_BALANCE,
                            line > 0 ? line : null,
                            item.materialCode(),
                            "Linha " + (line > 0 ? line : "?") + " (" + item.materialCode() + "): Quantidade faturada ("
                                    + invoicedQuantity.stripTrailingZeros().toPlainString() + ") excede o saldo pendente a receber ("
                                    + pendingQuantity.stripTrailingZeros().toPlainString() + ")",
                            pendingQuantity.stripTrailingZeros().toPlainString(),
                            invoicedQuantity.stripTrailingZeros().toPlainString(),
                            "+" + excess.stripTrailingZeros().toPlainString()
                    );
                }
            }
        }

        return true;
    }
}
