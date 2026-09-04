# 5. OAuth 2.0 Client Credentials and JWT Security

Date: 2026-09-04

## Context

The Purchase Order Integration Gateway connects external enterprise client ERPs (like SAP S/4HANA) and the central V360 platform. These communications are Machine-to-Machine (M2M) with no interactive human logins. Allowing unauthenticated access would expose confidential enterprise purchasing data and invoice amounts, while basic username/password schemes lack standard claims and token expiration controls.

## Decision

We adopt the **OAuth 2.0 Client Credentials Flow** with stateless, signed **JSON Web Tokens (JWT)**:
- An authentication endpoint `POST /api/v1/auth/token` accepts client credentials (`clientId` and `clientSecret`) and issues HMAC-SHA256 signed JWTs.
- The JWT payload embeds security roles (`ROLE_PLATFORM`, `ROLE_CLIENT`) and the immutable tenant identifier (`tenantCode`).
- Spring Security enforces:
  - **Platform Role (`ROLE_PLATFORM`)**: Global access to all endpoints, multi-client query filters, reconciliation, and audit reports.
  - **Client Role (`ROLE_CLIENT`)**: Strictly scoped tenant isolation; the client can only push its own ingestion payloads and read its own purchase orders.
- For developer and evaluator convenience, pre-configured credentials and automated token chaining are provided in `requests.http`, Postman collection, and Swagger UI.

## Consequences

- Direct alignment with modern enterprise and SAP integration standards (SAP BTP / Cloud Integration).
- Cryptographically verifiable stateless tokens with expiration timestamps.
- Zero external identity server dependency required to run or test the service locally.
