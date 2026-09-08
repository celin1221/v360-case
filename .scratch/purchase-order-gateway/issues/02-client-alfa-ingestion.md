# 02: Ingestão do Cliente Alfa Energia (JSON Aninhado)

**What to build:** O conector de ingestão do Cliente Alfa Energia capaz de processar payloads JSON contendo pedidos e itens aninhados, normalizar os dados para o modelo canônico (CNPJ sem máscara, status mapeado, datas em ISO-8601, quantidades e preços em `BigDecimal`), e salvar com upsert idempotente via porta de repositório.

**Blocked by:** 01-project-skeleton-and-oauth2-m2m

**Status:** done

- [x] Entidades canônicas de domínio `PurchaseOrder`, `PurchaseOrderItem`, `Vendor` e enum `OrderStatus`
- [x] Interface de porta `PurchaseOrderRepository` com implementações JPA e In-Memory
- [x] Componente `AlfaJsonAdapter` para ler e transformar o JSON aninhado do Cliente Alfa
- [x] Endpoint `POST /api/v1/ingestion/alfa` protegido por autenticação OAuth2 (perfil `alfa-client` ou `platform`)
- [x] Upsert idempotente no banco com base na chave composta `(clientId, poNumber)`
- [x] Testes unitários do adapter e testes de integração do endpoint com o payload de exemplo do desafio
