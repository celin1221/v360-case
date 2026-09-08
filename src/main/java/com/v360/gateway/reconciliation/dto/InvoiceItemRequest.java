package com.v360.gateway.reconciliation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record InvoiceItemRequest(
        Integer lineNumber,

        @NotBlank(message = "O código do material é obrigatório")
        String materialCode,

        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser positiva")
        BigDecimal quantity,

        BigDecimal unitPrice,

        BigDecimal totalPrice
) {
    @AssertTrue(message = "Informe unitPrice ou totalPrice para cada item da nota")
    public boolean isPricePresent() {
        return unitPrice != null || totalPrice != null;
    }

    public BigDecimal resolveUnitPrice() {
        if (unitPrice != null) {
            return unitPrice;
        }
        if (totalPrice != null && quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
            return totalPrice.divide(quantity, 4, RoundingMode.HALF_UP);
        }
        return null;
    }

    public BigDecimal resolveTotalPrice() {
        if (totalPrice != null) {
            return totalPrice;
        }
        if (unitPrice != null && quantity != null) {
            return unitPrice.multiply(quantity);
        }
        return null;
    }
}
