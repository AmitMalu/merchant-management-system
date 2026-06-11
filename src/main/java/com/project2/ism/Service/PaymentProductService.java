package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.PaymentModeDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentProductIDNameDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentProductRequestDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentProductResponseDTO;
import com.project2.ism.Model.Payment.PaymentProduct;
import com.project2.ism.Model.Payment.PaymentMode;
import com.project2.ism.Repository.PaymentModeRepository;
import com.project2.ism.Repository.PaymentProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PaymentProductService {

    private final PaymentProductRepository paymentProductRepository;
    private final PaymentModeRepository paymentModeRepository;

    public PaymentProductService(PaymentProductRepository paymentProductRepository, PaymentModeRepository paymentModeRepository) {
        this.paymentProductRepository = paymentProductRepository;
        this.paymentModeRepository = paymentModeRepository;
    }

    @Transactional
    public PaymentProductResponseDTO create(PaymentProductRequestDTO dto) {
        if (paymentProductRepository.existsByProductCode(dto.getProductCode())) {
            throw new IllegalArgumentException("Product code already exists");
        }

        PaymentProduct product = new PaymentProduct();
        product.setProductName(dto.getProductName());
        product.setProductCode(dto.getProductCode());
        product.setStatus(dto.getStatus() != null ? dto.getStatus() : true);

        // NEW: map modeIds
        Set<PaymentMode> modes = new HashSet<>(paymentModeRepository.findAllById(dto.getModeIds()));

        product.setAllowedModes(modes);

        return convert(paymentProductRepository.save(product));
    }

    @Transactional
    public PaymentProductResponseDTO update(Long id, PaymentProductRequestDTO dto) {
        PaymentProduct product = paymentProductRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (!product.getProductCode().equals(dto.getProductCode())
                && paymentProductRepository.existsByProductCode(dto.getProductCode())) {
            throw new IllegalArgumentException("Product code already exists");
        }

        product.setProductName(dto.getProductName());
        product.setProductCode(dto.getProductCode());
        product.setStatus(dto.getStatus());



        return convert(paymentProductRepository.save(product));
    }

    public PaymentProductResponseDTO getById(Long id) {
        return paymentProductRepository.findById(id)
                .map(this::convert)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
    }

    public Page<PaymentProductResponseDTO> getAll(Pageable pageable) {
        return paymentProductRepository.findAll(pageable)
                .map(this::convert);
    }

    public List<PaymentProductIDNameDTO> getAllProductsIdName() {
        return paymentProductRepository.findAll().stream()
                .map(p -> new PaymentProductIDNameDTO(
                        p.getId(),
                        p.getProductName(),
                        p.getProductCode()
                ))
                .collect(Collectors.toList());
    }


    @Transactional
    public void deleteById(Long id) {
        paymentProductRepository.deleteById(id);
    }

    private PaymentProductResponseDTO convert(PaymentProduct p) {
        PaymentProductResponseDTO dto = new PaymentProductResponseDTO();
        dto.setId(p.getId());
        dto.setProductName(p.getProductName());
        dto.setProductCode(p.getProductCode());
        dto.setStatus(p.getStatus());
        // NEW: allowed modes
        dto.setAllowedModes(
                p.getAllowedModes().stream()
                        .map(pm -> new PaymentModeDTO(pm.getId(), pm.getCode(), pm.getDescription()))
                        .collect(Collectors.toList())
        );

        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        return dto;
    }
}
