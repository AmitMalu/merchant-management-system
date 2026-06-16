package com.project2.ism.DTO.DigiLockerDTO;

import com.project2.ism.Enum.VerificationType;

public class DigilockerInitializeRequestDTO {

    private Long id;

    private VerificationType verificationType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public VerificationType getVerificationType() {
        return verificationType;
    }

    public void setVerificationType(VerificationType verificationType) {
        this.verificationType = verificationType;
    }

    public DigilockerInitializeRequestDTO(Long id, VerificationType verificationType) {
        this.id = id;
        this.verificationType = verificationType;
    }
}
