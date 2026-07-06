package com.project2.ism.Service;


import com.project2.ism.DTO.PaymentDTO.BillerCategoryListDto;
import com.project2.ism.DTO.PaymentDTO.VendorPurposeDTO;
import com.project2.ism.Model.Bbps.BillerCategoryList;
import com.project2.ism.Model.Payment.VendorPurpose;
import com.project2.ism.Repository.BillerCategoryListRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class VendorBillerCategoryService {

    private final BillerCategoryListRepository billerCategoryListRepository;

    public VendorBillerCategoryService(BillerCategoryListRepository billerCategoryListRepository) {
        this.billerCategoryListRepository = billerCategoryListRepository;
    }

    public void saveVendorBillerCategoryList(Long vendorId, List<BillerCategoryListDto> billerCategoryLists) {

        for (BillerCategoryListDto dto : billerCategoryLists) {

            Optional<BillerCategoryList> existing =
                    billerCategoryListRepository.findByVendorIdAndCode(vendorId, dto.getCode());

            BillerCategoryList billerCategoryList = existing.orElse(new BillerCategoryList());

            billerCategoryList.setVendorId(vendorId);
            billerCategoryList.setCode(dto.getCode());
            billerCategoryList.setDescription(dto.getDescription());
            billerCategoryList.setUpdatedAt(LocalDateTime.now());

            if (billerCategoryList.getId() == null) {
                billerCategoryList.setCreatedAt(LocalDateTime.now());
            }

            billerCategoryListRepository.save(billerCategoryList);
        }
    }
}
