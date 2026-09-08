package com.v360.gateway.reconciliation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InvoiceReconciliationRequest(
        String clientId,

        @NotBlank(message = "O número da nota fiscal é obrigatório")
        String invoiceNumber,

        @NotBlank(message = "O número do pedido de compra é obrigatório")
        String poNumber,

        @NotBlank(message = "O CNPJ do fornecedor é obrigatório")
        String vendorTaxId,

        @NotEmpty(message = "A nota fiscal deve conter ao menos um item")
        @Valid
        List<InvoiceItemRequest> items
) {
}
