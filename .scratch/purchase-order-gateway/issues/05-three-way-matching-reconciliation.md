# 05: Motor de Three-Way Matching e Conferência de Notas Fiscais

**What to build:** O endpoint `POST /api/v1/reconciliations` e o serviço de Three-Way Matching para conferência de notas fiscais de fornecedores contra pedidos de compra. Valida fornecedor, status do pedido, existência de materiais, saldo pendente a receber e preço unitário com tolerância de até R$ 0,01. Retorna aprovação ou lista detalhada e estruturada de divergências, gravando registro imutável de auditoria.

**Blocked by:** 04-purchase-order-query-api

**Status:** ready-for-agent

- [ ] DTO de entrada `InvoiceReconciliationRequest` contendo `poNumber`, `vendorTaxId` e lista de itens (`materialCode`, `quantity`, `unitPrice` ou `totalPrice`)
- [ ] Serviço de domínio `InvoiceReconciliationService` aplicando validações exaustivas:
  - Verificação de existência do pedido (`ORDER_NOT_FOUND`)
  - Verificação do CNPJ do fornecedor (`VENDOR_MISMATCH`)
  - Verificação do status do pedido (`ORDER_BLOCKED`, `ORDER_CLOSED`)
  - Verificação de existência do item (`ITEM_NOT_FOUND`)
  - Verificação de estouro de saldo pendente (`QUANTITY_EXCEEDS_PENDING_BALANCE`)
  - Verificação de divergência de preço unitário (`PRICE_MISMATCH`, tolerância R$ 0,01)
- [ ] Entidade e repositório `ReconciliationAuditRepository` para persistir o histórico de conferências
- [ ] DTO de resposta rico `ReconciliationResponse` com status `APPROVED`/`REJECTED` e lista estruturada de divergências
- [ ] Testes unitários com In-Memory repository cobrindo todos os cenários individuais e múltiplos de divergência
- [ ] Teste de integração do endpoint via MockMvc
