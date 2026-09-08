package com.v360.gateway.reconciliation.engine.rules;

import com.v360.gateway.domain.model.DivergenceType;
import com.v360.gateway.reconciliation.engine.ReconciliationContext;
import com.v360.gateway.reconciliation.engine.ReconciliationRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class PurchaseOrderExistenceRule implements ReconciliationRule {

    @Override
    public boolean evaluate(ReconciliationContext context) {
        if (context.getPurchaseOrder() == null) {
            context.addDivergence(
                    DivergenceType.ORDER_NOT_FOUND,
                    null,
                    null,
                    "Pedido de compra '" + context.getRequest().poNumber() + "' não encontrado para o cliente '" + context.getEffectiveClientId() + "'",
                    context.getRequest().poNumber(),
                    "NÃO ENCONTRADO",
                    null
            );
            return false; // Interrompe a cadeia, pois sem o pedido não é possível validar itens ou fornecedor
        }
        return true;
    }
}
