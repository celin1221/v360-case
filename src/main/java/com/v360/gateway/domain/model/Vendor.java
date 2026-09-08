package com.v360.gateway.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Vendor(
        @Column(name = "vendor_tax_id", length = 14, nullable = false)
        String taxId,

        @Column(name = "vendor_name", length = 255)
        String name
) {
    public Vendor {
        taxId = normalizeTaxId(taxId);
    }

    public static String normalizeTaxId(String rawTaxId) {
        if (rawTaxId == null) {
            return "";
        }
        return rawTaxId.replaceAll("\\D", "");
    }
}
