package com.v360.gateway.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "material_code", nullable = false, length = 100)
    private String materialCode;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "uom", nullable = false, length = 20)
    private String uom;

    @Column(name = "quantity_ordered", precision = 15, scale = 4, nullable = false)
    private BigDecimal quantityOrdered;

    @Column(name = "quantity_received", precision = 15, scale = 4, nullable = false)
    private BigDecimal quantityReceived;

    @Column(name = "unit_price", precision = 15, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    // Commercial packaging audit fields (ADR-0003)
    @Column(name = "original_uom", length = 20)
    private String originalUom;

    @Column(name = "original_quantity", precision = 15, scale = 4)
    private BigDecimal originalQuantity;

    @Column(name = "conversion_factor", precision = 10, scale = 4)
    private BigDecimal conversionFactor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    @JsonIgnore
    private PurchaseOrder order;

    public PurchaseOrderItem() {
    }

    public PurchaseOrderItem(Integer lineNumber, String materialCode, String description, String uom,
                             BigDecimal quantityOrdered, BigDecimal quantityReceived, BigDecimal unitPrice) {
        this.lineNumber = lineNumber;
        this.materialCode = materialCode;
        this.description = description;
        this.uom = uom;
        this.quantityOrdered = quantityOrdered != null ? quantityOrdered : BigDecimal.ZERO;
        this.quantityReceived = quantityReceived != null ? quantityReceived : BigDecimal.ZERO;
        this.unitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
    }

    public BigDecimal getPendingQuantity() {
        BigDecimal received = quantityReceived != null ? quantityReceived : BigDecimal.ZERO;
        BigDecimal ordered = quantityOrdered != null ? quantityOrdered : BigDecimal.ZERO;
        BigDecimal pending = ordered.subtract(received);
        return pending.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : pending;
    }

    public BigDecimal getTotalPrice() {
        BigDecimal ordered = quantityOrdered != null ? quantityOrdered : BigDecimal.ZERO;
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        return ordered.multiply(price);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUom() {
        return uom;
    }

    public void setUom(String uom) {
        this.uom = uom;
    }

    public BigDecimal getQuantityOrdered() {
        return quantityOrdered;
    }

    public void setQuantityOrdered(BigDecimal quantityOrdered) {
        this.quantityOrdered = quantityOrdered;
    }

    public BigDecimal getQuantityReceived() {
        return quantityReceived;
    }

    public void setQuantityReceived(BigDecimal quantityReceived) {
        this.quantityReceived = quantityReceived;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getOriginalUom() {
        return originalUom;
    }

    public void setOriginalUom(String originalUom) {
        this.originalUom = originalUom;
    }

    public BigDecimal getOriginalQuantity() {
        return originalQuantity;
    }

    public void setOriginalQuantity(BigDecimal originalQuantity) {
        this.originalQuantity = originalQuantity;
    }

    public BigDecimal getConversionFactor() {
        return conversionFactor;
    }

    public void setConversionFactor(BigDecimal conversionFactor) {
        this.conversionFactor = conversionFactor;
    }

    public PurchaseOrder getOrder() {
        return order;
    }

    public void setOrder(PurchaseOrder order) {
        this.order = order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PurchaseOrderItem that = (PurchaseOrderItem) o;
        return Objects.equals(lineNumber, that.lineNumber) && Objects.equals(materialCode, that.materialCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineNumber, materialCode);
    }
}
