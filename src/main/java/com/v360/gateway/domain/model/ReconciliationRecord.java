package com.v360.gateway.domain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "reconciliation_audits")
public class ReconciliationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reconciled_at", nullable = false)
    private Instant reconciledAt;

    @Column(name = "client_id", nullable = false, length = 50)
    private String clientId;

    @Column(name = "po_number", nullable = false, length = 50)
    private String poNumber;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Column(name = "vendor_tax_id", nullable = false, length = 20)
    private String vendorTaxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "reconciliation_divergences",
            joinColumns = @JoinColumn(name = "reconciliation_id")
    )
    private List<ReconciliationDivergence> divergences = new ArrayList<>();

    public ReconciliationRecord() {
    }

    public ReconciliationRecord(String clientId, String poNumber, String invoiceNumber, String vendorTaxId,
                                ReconciliationStatus status, Instant reconciledAt, List<ReconciliationDivergence> divergences) {
        this.clientId = clientId;
        this.poNumber = poNumber;
        this.invoiceNumber = invoiceNumber;
        this.vendorTaxId = vendorTaxId;
        this.status = status;
        this.reconciledAt = reconciledAt != null ? reconciledAt : Instant.now();
        if (divergences != null) {
            this.divergences.addAll(divergences);
        }
    }

    public void addDivergence(ReconciliationDivergence divergence) {
        this.divergences.add(divergence);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getReconciledAt() {
        return reconciledAt;
    }

    public void setReconciledAt(Instant reconciledAt) {
        this.reconciledAt = reconciledAt;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getVendorTaxId() {
        return vendorTaxId;
    }

    public void setVendorTaxId(String vendorTaxId) {
        this.vendorTaxId = vendorTaxId;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public void setStatus(ReconciliationStatus status) {
        this.status = status;
    }

    public List<ReconciliationDivergence> getDivergences() {
        return divergences;
    }

    public void setDivergences(List<ReconciliationDivergence> divergences) {
        this.divergences = divergences != null ? divergences : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReconciliationRecord that = (ReconciliationRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
