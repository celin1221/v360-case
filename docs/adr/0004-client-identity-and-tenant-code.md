# 4. Client Identity and Tenant Code

Date: 2026-09-04

## Context

Different client organizations connect their ERPs to V360. Using mutable display names (e.g. "Alfa", "Alfa Energia") as database keys or query filters causes namespace collisions, vulnerability to corporate rebrandings, and risks data leakage if two clients share identical or similar trade names.

## Decision

We model client identity using an immutable, unique tenant code (e.g. `CLI-ALFA-001`, `CLI-BETA-002`, `CLI-GAMA-003`) or system UUID alongside descriptive metadata (`name`, `taxId`). In the API query filters and purchase order associations, filtering is performed primarily by this immutable identifier, with support for slug aliases while ensuring complete tenant disambiguation.

## Consequences

- Zero risk of naming collisions or data leaks between distinct corporate clients.
- Clean separation between tenant identity and display metadata.
- Filters in `GET /api/v1/purchase-orders?clientId=CLI-ALFA-001` are deterministic.
