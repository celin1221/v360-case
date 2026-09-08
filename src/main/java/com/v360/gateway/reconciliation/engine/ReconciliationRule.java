package com.v360.gateway.reconciliation.engine;

public interface ReconciliationRule {
    /**
     * Evaluates a reconciliation rule against the context.
     *
     * @param context The reconciliation context containing request, purchase order, and accumulated divergences.
     * @return true to continue executing the chain of responsibility, or false to short-circuit the chain (e.g. if order is not found).
     */
    boolean evaluate(ReconciliationContext context);
}
