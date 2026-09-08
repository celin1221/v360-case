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
import java.util.*;
import java.util.stream.Collectors;

@Component
@Order(50)
public class ItemPendingBalanceRule implements ReconciliationRule {

    @Override
    public boolean evaluate(ReconciliationContext context) {
        PurchaseOrder order = context.getPurchaseOrder();
        if (order == null || context.getRequest().items() == null) {
            return true;
        }

        // Agrupa itens da nota fiscal por código de material para verificar estouro cumulativo
        Map<String, List<InvoiceItemRequest>> itemsByMaterial = new LinkedHashMap<>();
        for (InvoiceItemRequest item : context.getRequest().items()) {
            if (item.materialCode() != null) {
                String key = item.materialCode().trim().toUpperCase();
                itemsByMaterial.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            }
        }

        for (Map.Entry<String, List<InvoiceItemRequest>> entry : itemsByMaterial.entrySet()) {
            String materialCode = entry.getKey();
            List<InvoiceItemRequest> groupItems = entry.getValue();

            Optional<PurchaseOrderItem> matchingOrderItem = order.findItemByMaterialCode(materialCode);
            if (matchingOrderItem.isPresent()) {
                PurchaseOrderItem orderItem = matchingOrderItem.get();
                BigDecimal pendingQuantity = orderItem.getPendingQuantity();

                BigDecimal totalInvoicedQuantity = groupItems.stream()
                        .map(InvoiceItemRequest::quantity)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (totalInvoicedQuantity.compareTo(pendingQuantity) > 0) {
                    BigDecimal excess = totalInvoicedQuantity.subtract(pendingQuantity);

                    String lineInfo;
                    Integer targetLine;
                    if (groupItems.size() == 1) {
                        InvoiceItemRequest single = groupItems.get(0);
                        targetLine = single.lineNumber();
                        lineInfo = "Linha " + (targetLine != null ? targetLine : "?");
                    } else {
                        targetLine = groupItems.get(0).lineNumber();
                        String lines = groupItems.stream()
                                .map(i -> String.valueOf(i.lineNumber() != null ? i.lineNumber() : "?"))
                                .collect(Collectors.joining(", "));
                        lineInfo = "Linhas [" + lines + "] (acumulado)";
                    }

                    context.addDivergence(
                            DivergenceType.QUANTITY_EXCEEDS_PENDING_BALANCE,
                            targetLine,
                            orderItem.getMaterialCode(),
                            lineInfo + " (" + orderItem.getMaterialCode() + "): Quantidade total faturada ("
                                    + totalInvoicedQuantity.stripTrailingZeros().toPlainString() + ") excede o saldo pendente a receber ("
                                    + pendingQuantity.stripTrailingZeros().toPlainString() + ")",
                            pendingQuantity.stripTrailingZeros().toPlainString(),
                            totalInvoicedQuantity.stripTrailingZeros().toPlainString(),
                            "+" + excess.stripTrailingZeros().toPlainString()
                    );
                }
            }
        }

        return true;
    }
}
