# Specification: Purchase Order Integration Gateway & Reconciliation Engine

## Problem Statement

A plataforma central V360 precisa automatizar a conferência (three-way matching) de notas fiscais de fornecedores contra pedidos de compra corporativos para evitar conferências manuais lentas, caras e propensas a erros no contas a pagar. No entanto, cada cliente corporativo da V360 possui um ERP distinto (como SAP, Totvs ou sistemas legados) que exporta dados de compras em formatos heterogêneos e conflitantes (JSON aninhado, múltiplos CSVs com formatação brasileira ou JSON achatado com unidades de embalagem comercial como caixas). Se a plataforma V360 tentasse suportar cada formato diretamente, haveria alto acoplamento e qualquer alteração de formato em um cliente quebraria a plataforma para todos os outros.

## Solution

Construção de uma camada intermediária de integração em Java 21 e Spring Boot 3 que atua como Gateway de Ingestão e Motor de Conferência. O serviço ingere pedidos de compra dos clientes, normaliza todos os dados para um Modelo Canônico único via adaptadores isolados (padrão Strategy), expõe APIs REST padronizadas para consulta e filtros com isolamento multi-tenant, e executa a conferência automatizada de notas fiscais contra pedidos de compra, reportando divergências de forma detalhada e auditável com métricas analíticas.

## User Stories

1. As a V360 Platform Operator, I want to authenticate via OAuth 2.0 Client Credentials, so that I receive a secure JWT token to access platform endpoints.
2. As a Client System, I want to authenticate via OAuth 2.0 Client Credentials with tenant-scoped permissions, so that I can securely transmit purchasing data without exposing other clients.
3. As a Client Alfa Integration, I want to send nested JSON purchase orders, so that my orders are ingested and normalized into canonical purchase orders.
4. As a Client Beta Integration, I want to upload Brazilian-formatted CSV files (header and items), so that comma-decimal numbers, Brazilian dates, and masked CNPJs are correctly parsed into canonical purchase orders.
5. As a Client Gama Integration, I want to transmit flat JSON items containing packaging units and conversion factors, so that quantities and unit prices are normalized to base units while preserving commercial packaging metadata.
6. As a Procurement Operator, I want to resubmit a modified purchase order, so that the gateway updates existing records idempotently without creating duplicates.
7. As a V360 Operator, I want to query purchase orders filtering by client identifier, so that I can inspect orders from a specific customer.
8. As a V360 Operator, I want to query purchase orders filtering by vendor CNPJ (with or without mask), so that I can inspect orders associated with a particular supplier.
9. As a V360 Operator, I want to query purchase orders filtering by canonical order status (OPEN, CLOSED, BLOCKED), so that I can isolate actionable orders.
10. As a V360 Operator, I want to query only purchase orders that still have a pending balance, so that I can focus on deliveries that are still outstanding.
11. As a V360 Operator, I want paginated query results with total counts, so that high volumes of purchase orders can be navigated efficiently.
12. As a V360 Operator, I want to retrieve full details of a specific purchase order, so that I can see the exact ordered, received, and calculated pending balance for every item.
13. As a V360 Platform, I want to submit an incoming supplier invoice for reconciliation against a purchase order, so that I can determine if payment can be approved.
14. As a Accounts Payable Auditor, I want reconciliation to verify if the invoice vendor matches the purchase order vendor, so that payments are not routed to fraudulent or incorrect entities.
15. As a Accounts Payable Auditor, I want reconciliation to verify if the purchase order is in OPEN status, so that invoices against BLOCKED or CLOSED orders are rejected.
16. As a Accounts Payable Auditor, I want reconciliation to verify if invoiced materials exist in the purchase order, so that unauthorized materials are rejected.
17. As a Accounts Payable Auditor, I want reconciliation to verify that invoiced quantities do not exceed the item pending balance, so that over-deliveries are flagged.
18. As a Accounts Payable Auditor, I want reconciliation to verify that item unit prices match agreed purchase order prices with a tolerance of up to R$ 0.01, so that price discrepancies are caught while rounding errors are tolerated.
19. As a Accounts Payable Auditor, I want an exhaustive list of all divergences found in an invoice, so that the operator knows every issue in a single evaluation without guessing.
20. As a Operations Manager, I want an audit report detailing total reconciliations, approvals, rejections, approval rates, and a breakdown of rejection reasons, so that I can identify bottlenecks and recurring vendor discrepancies.

## Implementation Decisions

- **Architectural Pattern**: Hexagonal / Clean Architecture with Ports and Adapters. Business logic and canonical entities are decoupled from database persistence and external protocols (ADR-0001, ADR-0002).
- **Client Ingestion Strategy**: Each client format is parsed by a dedicated adapter (`AlfaJsonAdapter`, `BetaCsvAdapter`, `GamaJsonAdapter`), transforming client-specific structures into canonical `PurchaseOrder` entities without leaking client formats into the core.
- **Client Identity & Multi-Tenancy**: Clients are identified by immutable tenant codes (e.g. `CLI-ALFA-001`, `CLI-BETA-002`, `CLI-GAMA-003`), guaranteeing isolation and preventing collision (ADR-0004).
- **Unit Normalization**: Cliente Gama purchase orders in commercial packaging units (`CX`) with `fator_conv` are normalized during ingestion to base units (`UN`) and unit prices, preserving original commercial attributes for auditability (ADR-0003).
- **Security**: OAuth 2.0 Client Credentials M2M flow issuing signed JWTs with `ROLE_PLATFORM` and tenant-scoped `ROLE_CLIENT` (ADR-0005).
- **Persistence Ports**: `PurchaseOrderRepository` and `ReconciliationAuditRepository` interfaces implemented with Spring Data JPA (backed by persistent H2 file and Postgres Docker profile) and a concurrent in-memory implementation for high-speed unit testing.
- **Idempotent Ingestion**: Ingestion matches on composite business key `(clientId, poNumber)`, performing upserts on headers and items.
- **Reconciliation Engine**: Pure, stateless validation service returning a comprehensive `ReconciliationResult` with `APPROVED` or `REJECTED` status and structured `Divergence` list, recording an immutable audit event in persistence.

## Testing Decisions

- **Testing External Behavior**: Tests evaluate observable system behavior at public seams (HTTP REST endpoints and Domain Service Ports) rather than asserting internal private methods.
- **Modules Tested**:
  - Ingestion Adapters: Testing valid payloads, corrupt payloads, malformed numbers, and conversion factors.
  - Three-Way Matching Engine: Testing all divergence types individually and cumulatively (vendor mismatch, blocked order, missing item, balance exceeded, price difference, within-tolerance rounding).
  - Security & Tenant Guard: Testing token generation, unauthorized access (401), cross-tenant isolation (403), and platform superuser access.
  - Query Filters: Testing multi-criteria filtering, pagination, and calculated pending balances.
- **Test Slices**:
  - Fast domain unit tests using the In-Memory repository implementation.
  - End-to-end integration tests using `@SpringBootTest` and `MockMvc`.

## Out of Scope

- Human UI web interface (the project is strictly a backend REST API service).
- Physical warehouse goods receipt processing (handled by ERP; this service focuses on purchase orders and fiscal invoice matching).
- External identity provider infrastructure (Keycloak, Okta) deployment (JWTs are self-contained and issued directly by the Spring Boot gateway).

## Further Notes

- Ingestion seed data is automatically loaded at startup to ensure immediate testability for evaluators without manual data entry.
- Swagger UI will be exposed at `/swagger-ui.html` with Bearer JWT authorization configured.
- A ready-to-run `requests.http` file will provide single-click token chaining for all operational scenarios.
