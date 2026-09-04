# 1. Canonical Data Model and Adapter Architecture

Date: 2026-09-04

## Context

The V360 platform needs to query purchase orders and reconcile supplier invoices against diverse client ERP systems (e.g. Alfa Energia exporting nested JSON, Beta Alimentos exporting split semicolon-delimited CSVs, and Gama Logística exporting flat JSON with conversion factors). Allowing client-specific schemas to penetrate the core matching logic would create tight coupling, where any client change or new client onboarding would break the core platform.

## Decision

We adopt a **Canonical Data Model** combined with the **Strategy / Ports & Adapters** pattern.
- The core platform interacts strictly with unified canonical entities (`PurchaseOrder`, `PurchaseOrderItem`, `OrderStatus`, `Reconciliation`).
- Each client has an isolated ingestion adapter (`AlfaJsonAdapter`, `BetaCsvAdapter`, `GamaJsonAdapter`) responsible for transforming external schemas, Brazilian currency formats, masks, and unit conversion factors into the canonical model.
- Dedicated REST endpoints (`/api/v1/ingestion/{client}`) receive raw payloads, and an idempotent upsert strategy (`clientId` + `poNumber`) handles payload resubmission.

## Consequences

- Onboarding a future client (e.g. with XML or EDI) requires only implementing a new adapter without modifying domain or reconciliation logic.
- Core reconciliation rules operate deterministically on standardized types (`BigDecimal`, normalized 14-digit CNPJ, canonical enums).
- Slightly more upfront code to map each format into canonical domain structures.
