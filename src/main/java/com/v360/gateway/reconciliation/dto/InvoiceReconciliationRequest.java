package com.v360.gateway.reconciliation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InvoiceReconciliationRequest(
        String clientId,

        String invoiceNumber,

        @NotBlank(message = "O número do purchase order é obrigatório")
        String poNumber,

        @NotBlank(message = "O CNPJ do vendor é obrigatório")
        String vendorTaxId,

        @NotEmpty(message = "A invoice deve conter ao menos um item")
        @Valid
        List<InvoiceItemRequest> items
) {
}
