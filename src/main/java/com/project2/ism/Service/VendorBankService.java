package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.VendorBankDTO;
import com.project2.ism.Model.Payment.VendorBank;
import com.project2.ism.Repository.VendorBankRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class VendorBankService {


    private final VendorBankRepository vendorBankRepository;

    public VendorBankService(VendorBankRepository vendorBankRepository) {
        this.vendorBankRepository = vendorBankRepository;
    }

    public void saveVendorBankList(Long vendorId, List<VendorBankDTO> banks) {
        for (VendorBankDTO dto : banks) {

            Optional<VendorBank> existing = vendorBankRepository
                    .findByVendorIdAndBankName(vendorId, dto.getBankName());

            VendorBank bank = existing.orElse(new VendorBank());

            bank.setVendorId(vendorId);
            bank.setBankName(dto.getBankName());
            bank.setBankCode(dto.getBankCode());
            bank.setUpdatedAt(LocalDateTime.now());

            if (bank.getId() == null) {
                bank.setCreatedAt(LocalDateTime.now());
            }

            vendorBankRepository.save(bank);
        }
    }

    public List<VendorBank> getVendorBankList(Long vendorId) {
        return vendorBankRepository.findByVendorId(vendorId);
    }
}

