# 04: API Canônica de Consulta e Filtros de Pedidos

**What to build:** Endpoints REST de consulta para a plataforma V360 visualizar os pedidos de todos os clientes em formato único, com suporte a paginação e filtros úteis para a operação: por cliente de origem (`clientId`), por fornecedor (`vendorTaxId`), por situação (`status`), e por saldo pendente a receber (`onlyPendingBalance`), além de detalhe do pedido com cálculo explícito do saldo pendente por item.

**Blocked by:** 02-client-alfa-ingestion, 03-client-beta-ingestion

**Status:** done

- [x] Endpoint `GET /api/v1/purchase-orders` com suporte a `Pageable` do Spring (paginação e ordenação)
- [x] Filtro por `clientId` (código de tenant) com isolamento multi-tenant (clientes só veem seus dados; `ROLE_PLATFORM` vê todos)
- [x] Filtro por `vendorTaxId` aceitando formato limpo ou com máscara
- [x] Filtro por `status` (`OPEN`, `CLOSED`, `BLOCKED`)
- [x] Filtro booleano `onlyPendingBalance` retornando apenas pedidos onde ao menos um item tem `pendingQuantity > 0`
- [x] Endpoint `GET /api/v1/purchase-orders/{id}` e `GET /api/v1/purchase-orders/by-number/{poNumber}` com detalhamento dos itens e `pendingQuantity = max(0, quantityOrdered - quantityReceived)`
- [x] Testes de integração validando cada combinação de filtro e paginação
