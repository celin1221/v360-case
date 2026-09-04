# 3. Base Unit Normalization with Commercial Packaging Preservation

Date: 2026-09-04

## Context

Different clients order in different packaging units. For instance, Cliente Gama Logística orders in boxes (`CX`), specifying `fator_conv` (e.g. 10 boxes with factor 12 equals 120 units at R$ 1,200.00/box). However, supplier invoices (Notas Fiscais) are issued in standard units (`UN`) with item unit prices. If reconciliation logic had to handle packaging conversions on the fly, matching rules would become complex and fragile.

## Decision

We normalize all purchase orders to the base unit (`UN`) during ingestion:
- `quantityOrdered` = `qtd_ped * fator_conv`
- `quantityReceived` = `qtd_rec * fator_conv`
- `unitPrice` = `(preco_unit_centavos / 100) / fator_conv`
- In addition, we preserve original commercial packaging attributes (`originalUom = "CX"`, `originalQuantity`, `conversionFactor`) on the item entity for end-to-end auditability and transparency.

## Consequences

- The three-way matching reconciliation engine operates uniformly in base units without knowing client-specific packaging rules.
- Any discrepancy is reported transparently to both the buyer and the vendor without unit confusion.
- Additional fields on `PurchaseOrderItem` to store original client packaging details.
