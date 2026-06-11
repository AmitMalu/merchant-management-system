package com.project2.ism.DTO;

import java.util.List;

public class SchemeGroupedResponseDTO {
    private String schemeCode;
    private String description;
    private String customerType;
    private Double rentalByMonth;
    private List<CustomerSchemeAssignmentDTO> assignments;

    public String getSchemeCode() {
        return schemeCode;
    }

    public void setSchemeCode(String schemeCode) {
        this.schemeCode = schemeCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public Double getRentalByMonth() {
        return rentalByMonth;
    }

    public void setRentalByMonth(Double rentalByMonth) {
        this.rentalByMonth = rentalByMonth;
    }

    public List<CustomerSchemeAssignmentDTO> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<CustomerSchemeAssignmentDTO> assignments) {
        this.assignments = assignments;
    }
}
