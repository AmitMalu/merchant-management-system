package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.VendorStateDTO;
import com.project2.ism.DTO.PaymentDTO.VendorStateResponseDTO;
import com.project2.ism.Model.Payment.VendorState;
import com.project2.ism.Repository.VendorStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class VendorStateService {


    private final VendorStateRepository vendorStateRepository;

    public VendorStateService(VendorStateRepository vendorStateRepository) {
        this.vendorStateRepository = vendorStateRepository;
    }

    public void saveVendorStateList(Long vendorId, List<VendorStateDTO> states) {

        for (VendorStateDTO dto : states) {

            Optional<VendorState> existing =
                    vendorStateRepository.findByVendorIdAndStateName(vendorId, dto.getStateName());

            VendorState state = existing.orElse(new VendorState());

            state.setVendorId(vendorId);
            state.setStateName(dto.getStateName());
            state.setStateCode(dto.getStateCode());
            state.setUpdatedAt(LocalDateTime.now());

            if (state.getId() == null) {
                state.setCreatedAt(LocalDateTime.now());
            }

            vendorStateRepository.save(state);
        }
    }

    public List<VendorStateResponseDTO> getVendorStates(Long vendorId) {
        return vendorStateRepository.findByVendorId(vendorId)
                .stream()
                .map(s -> {
                    VendorStateResponseDTO dto = new VendorStateResponseDTO();
                    dto.setStateName(s.getStateName());
                    dto.setStateCode(s.getStateCode());
                    return dto;
                })
                .toList();
    }
}
