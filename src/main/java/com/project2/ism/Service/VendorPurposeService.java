package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.VendorPurposeDTO;
import com.project2.ism.Model.Payment.VendorPurpose;
import com.project2.ism.Repository.VendorPurposeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class VendorPurposeService {


    private final VendorPurposeRepository vendorPurposeRepository;

    public VendorPurposeService(VendorPurposeRepository vendorPurposeRepository) {
        this.vendorPurposeRepository = vendorPurposeRepository;
    }

    public void saveVendorPurposes(Long vendorId, List<VendorPurposeDTO> purposes) {

        for (VendorPurposeDTO dto : purposes) {

            Optional<VendorPurpose> existing =
                    vendorPurposeRepository.findByVendorIdAndPurposeName(vendorId, dto.getPurposeName());

            VendorPurpose purpose = existing.orElse(new VendorPurpose());

            purpose.setVendorId(vendorId);
            purpose.setPurposeName(dto.getPurposeName());
            purpose.setPurposeCode(dto.getPurposeCode());
            purpose.setUpdatedAt(LocalDateTime.now());

            if (purpose.getId() == null) {
                purpose.setCreatedAt(LocalDateTime.now());
            }

            vendorPurposeRepository.save(purpose);
        }
    }

    public List<VendorPurposeDTO> getVendorPurposes(Long vendorId) {
        return vendorPurposeRepository.findByVendorId(vendorId)
                .stream()
                .map(p -> {
                    VendorPurposeDTO dto = new VendorPurposeDTO();
                    dto.setPurposeName(p.getPurposeName());
                    dto.setPurposeCode(p.getPurposeCode());
                    return dto;
                })
                .toList();
    }
}

