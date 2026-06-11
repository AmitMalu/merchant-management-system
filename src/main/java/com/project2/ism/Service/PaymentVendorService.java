package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.PaymentVendorRequestDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentVendorResponseDTO;
import com.project2.ism.DTO.Vendor.VendorIDNameDTO;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Repository.PaymentVendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentVendorService {

    private final PaymentVendorRepository paymentVendorRepository;

    public PaymentVendorService(PaymentVendorRepository paymentVendorRepository) {
        this.paymentVendorRepository = paymentVendorRepository;
    }

    @Transactional
    public PaymentVendorResponseDTO create(PaymentVendorRequestDTO dto) {

        if (paymentVendorRepository.existsByVendorName(dto.getVendorName())) {
            throw new IllegalArgumentException("Vendor name already exists");
        }

        PaymentVendor pv = new PaymentVendor();
        pv.setVendorName(dto.getVendorName());
        pv.setSupportedModes(dto.getSupportedModes());
        pv.setStatus(dto.getStatus() != null ? dto.getStatus() : true);

        return convert(paymentVendorRepository.save(pv));
    }

    @Transactional
    public PaymentVendorResponseDTO update(Long id, PaymentVendorRequestDTO dto) {

        PaymentVendor pv = paymentVendorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));

        if (!pv.getVendorName().equals(dto.getVendorName())
                && paymentVendorRepository.existsByVendorName(dto.getVendorName())) {
            throw new IllegalArgumentException("Vendor name already exists");
        }

        pv.setVendorName(dto.getVendorName());
        pv.setSupportedModes(dto.getSupportedModes());
        pv.setStatus(dto.getStatus());

        return convert(paymentVendorRepository.save(pv));
    }

    public PaymentVendorResponseDTO getById(Long id) {
        return paymentVendorRepository.findById(id)
                .map(this::convert)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
    }

    public Page<PaymentVendorResponseDTO> getAll(Pageable pageable) {
        return paymentVendorRepository.findAll(pageable)
                .map(this::convert);
    }


    public List<VendorIDNameDTO> getAllVendorsIdAndName() {
        return paymentVendorRepository.findAll().stream()
                .map(v -> new VendorIDNameDTO(v.getId(), v.getVendorName()))
                .collect(Collectors.toList());
    }
    @Transactional
    public void deleteById(Long id) {
        paymentVendorRepository.deleteById(id);
    }

    private PaymentVendorResponseDTO convert(PaymentVendor pv) {
        PaymentVendorResponseDTO dto = new PaymentVendorResponseDTO();
        dto.setId(pv.getId());
        dto.setVendorName(pv.getVendorName());
        dto.setSupportedModes(pv.getSupportedModes());
        dto.setStatus(pv.getStatus());
        dto.setCreatedAt(pv.getCreatedAt());
        dto.setUpdatedAt(pv.getUpdatedAt());
        return dto;
    }
}
