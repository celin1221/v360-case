package com.v360.gateway.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

@Embeddable
public class ReconciliationDivergence {

    @Enumerated(EnumType.STRING)
    @Column(name = "divergence_code", nullable = false, length = 50)
    private DivergenceType code;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "material_code", length = 100)
    private String materialCode;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "expected_value", length = 100)
    private String expectedValue;

    @Column(name = "actual_value", length = 100)
    private String actualValue;

    @Column(name = "difference", length = 100)
    private String difference;

    public ReconciliationDivergence() {
    }

    public ReconciliationDivergence(DivergenceType code, Integer lineNumber, String materialCode,
                                    String description, String expectedValue, String actualValue, String difference) {
        this.code = code;
        this.lineNumber = lineNumber;
        this.materialCode = materialCode;
        this.description = description;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.difference = difference;
    }

    public DivergenceType getCode() {
        return code;
    }

    public void setCode(DivergenceType code) {
        this.code = code;
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

    public String getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    public String getActualValue() {
        return actualValue;
    }

    public void setActualValue(String actualValue) {
        this.actualValue = actualValue;
    }

    public String getDifference() {
        return difference;
    }

    public void setDifference(String difference) {
        this.difference = difference;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReconciliationDivergence that = (ReconciliationDivergence) o;
        return code == that.code &&
                Objects.equals(lineNumber, that.lineNumber) &&
                Objects.equals(materialCode, that.materialCode) &&
                Objects.equals(description, that.description) &&
                Objects.equals(expectedValue, that.expectedValue) &&
                Objects.equals(actualValue, that.actualValue) &&
                Objects.equals(difference, that.difference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, lineNumber, materialCode, description, expectedValue, actualValue, difference);
    }
}
