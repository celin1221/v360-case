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
import java.math.RoundingMode;
import java.util.Optional;

@Component
@Order(60)
public class ItemPriceToleranceRule implements ReconciliationRule {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

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
                BigDecimal invoiceUnitPrice = item.resolveUnitPrice();
                BigDecimal orderUnitPrice = orderItem.getUnitPrice();

                if (invoiceUnitPrice != null && orderUnitPrice != null) {
                    BigDecimal diff = invoiceUnitPrice.subtract(orderUnitPrice);
                    if (diff.abs().compareTo(TOLERANCE) > 0) {
                        int line = item.lineNumber() != null ? item.lineNumber() : 0;
                        String sign = diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                        context.addDivergence(
                                DivergenceType.PRICE_MISMATCH,
                                line > 0 ? line : null,
                                item.materialCode(),
                                "Linha " + (line > 0 ? line : "?") + " (" + item.materialCode() + "): Preço unitário da nota (R$ "
                                        + invoiceUnitPrice.setScale(2, RoundingMode.HALF_UP)
                                        + ") diverge do acordado no pedido (R$ "
                                        + orderUnitPrice.setScale(2, RoundingMode.HALF_UP)
                                        + ") além da tolerância de R$ 0,01",
                                orderUnitPrice.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                                invoiceUnitPrice.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                                sign + diff.setScale(2, RoundingMode.HALF_UP).toPlainString()
                        );
                    }
                }
            }
        }

        return true;
    }
}
