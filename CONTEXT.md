# Purchase Order Integration Gateway

Intermediate integration service connecting diverse enterprise client procurement systems to the V360 platform through a single canonical contract, providing purchase order consultation, invoice reconciliation (three-way matching), and audit reporting.

## Language

**Client**:
The enterprise buyer contracting V360, identified by an immutable unique identifier/tenant code rather than a mutable corporate name.
_Avoid_: Customer, Empresa, Tenant, Comprador

**Purchase Order**:
A document issued by a client registering agreed items, materials, quantities, and unit prices with a vendor.
_Avoid_: Order, Pedido, PO

**Invoice**:
A fiscal document issued by a vendor charging for delivered materials, submitted for three-way matching against a purchase order.
_Avoid_: NF, Bill, Fatura

**Vendor**:
The selling party that receives the purchase order and issues invoices, identified canonically by a 14-digit unmasked CNPJ tax ID.
_Avoid_: Supplier, Emitente, Fornecedor

**Reconciliation**:
The automated three-way matching verification of an invoice against its corresponding purchase order to approve or reject payment.
_Avoid_: Matching, Validação, Checagem, Conferência

**Divergence**:
A specific, structured discrepancy between invoice data and purchase order terms (e.g. vendor mismatch, blocked order, missing item, balance exceeded, price difference).
_Avoid_: Erro, Falha, Bug, Exception

**Order Status**:
The canonical lifecycle state of a purchase order: `OPEN` (active and receivable), `CLOSED` (fully fulfilled or ended), or `BLOCKED` (temporarily frozen by client).
_Avoid_: Estado, Situacao, Status do pedido

**Pending Balance**:
The remaining quantity of an item that has not yet been received (`quantityOrdered - quantityReceived`), defining the upper bound for invoice quantity.
_Avoid_: Saldo restante, Qtd a receber, Saldo em aberto

**Conversion Factor**:
A numerical multiplier used when a client orders in a commercial packaging unit (e.g. Box / CX) to convert quantities and unit prices into canonical base units (UN) matching supplier invoices.
_Avoid_: Multiplicador, Taxa de conversão, Fator
