# 2. Repository Port and Dual In-Memory/JPA Implementations

Date: 2026-09-04

## Context

The service requires persistence for purchase orders and reconciliation audit records. While Spring Data JPA directly coupled to controllers or services is common in quick prototypes, it tightly couples the domain and business rules to Hibernate and database drivers. Furthermore, unit and integration tests across services and reconciliation logic would require spinning up a database context, slowing down test execution.

## Decision

We define domain repository interfaces (`PurchaseOrderRepository` and `ReconciliationAuditRepository`) as domain ports (Hexagonal Architecture / DIP):
- **JPA Implementation**: Active in production (`application.yml`), leveraging Spring Data JPA with PostgreSQL 16 as the primary production and containerized datasource by default (and H2 persistent file database via the local fallback profile `application-h2.yml`).
- **In-Memory Implementation**: Implemented with concurrent thread-safe in-memory collections (`ConcurrentHashMap`), enabled in test slices or via Spring profile (`@Profile("in-memory")`), allowing fast, isolated testing of domain logic, adapters, and reconciliations without database overhead.

## Consequences

- Services depend solely on the domain interface, not on JPA or Hibernate abstractions.
- High testability and versatility for testing edge cases without database migrations.
- Minimal overhead in maintaining an in-memory implementation alongside the JPA adapter.
