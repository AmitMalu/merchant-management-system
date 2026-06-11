package com.project2.ism.DTO.PricingSchemesDTOS;

import java.util.List;

/**
        * Response wrapper containing list of schemes and global warning
 */
public class PricingSchemesResponseDTO {
    private List<PricingSchemeWarningDTO> schemes;
    private String globalWarning;  // Warning if no vendor rates exist

    public PricingSchemesResponseDTO() {}

    public PricingSchemesResponseDTO(List<PricingSchemeWarningDTO> schemes, String globalWarning) {
        this.schemes = schemes;
        this.globalWarning = globalWarning;
    }

    // Getters and Setters
    public List<PricingSchemeWarningDTO> getSchemes() { return schemes; }
    public void setSchemes(List<PricingSchemeWarningDTO> schemes) { this.schemes = schemes; }

    public String getGlobalWarning() { return globalWarning; }
    public void setGlobalWarning(String globalWarning) { this.globalWarning = globalWarning; }
}
