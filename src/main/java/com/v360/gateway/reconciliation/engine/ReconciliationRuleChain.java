package com.v360.gateway.reconciliation.engine;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReconciliationRuleChain {

    private final List<ReconciliationRule> rules;

    public ReconciliationRuleChain(List<ReconciliationRule> rules) {
        this.rules = rules;
    }

    public void execute(ReconciliationContext context) {
        for (ReconciliationRule rule : rules) {
            boolean continueChain = rule.evaluate(context);
            if (!continueChain) {
                break;
            }
        }
    }

    public List<ReconciliationRule> getRules() {
        return rules;
    }
}
