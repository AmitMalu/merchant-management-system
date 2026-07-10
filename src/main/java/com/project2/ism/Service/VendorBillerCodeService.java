package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.BillerCodeListDto;
import com.project2.ism.Model.Bbps.BillerCategoryList;
import com.project2.ism.Model.Bbps.BillerCodeList;
import com.project2.ism.Repository.BillerCategoryListRepository;
import com.project2.ism.Repository.BillerCodeListRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class VendorBillerCodeService {

    private final BillerCategoryListRepository billerCategoryListRepository;
    private final BillerCodeListRepository billerCodeListRepository;

    public VendorBillerCodeService(BillerCategoryListRepository billerCategoryListRepository, BillerCodeListRepository billerCodeListRepository) {
        this.billerCategoryListRepository = billerCategoryListRepository;
        this.billerCodeListRepository = billerCodeListRepository;
    }

    public void saveVendorBillerCodeList(
            Long vendorId,
            String billerCategoryCode,
            List<BillerCodeListDto> billerCodeLists
    ) {
        BillerCategoryList category = billerCategoryListRepository
                .findByVendorIdAndCode(vendorId, billerCategoryCode)
                .orElseThrow(() -> new RuntimeException(
                        "Biller category not found for code: " + billerCategoryCode
                ));

        for (BillerCodeListDto dto : billerCodeLists) {

            Optional<BillerCodeList> existing =
                    billerCodeListRepository.findByVendorIdAndBillerCategoryIdAndCode(
                            vendorId,
                            category.getId(),
                            dto.getCode()
                    );

            BillerCodeList billerCode = existing.orElse(new BillerCodeList());

            billerCode.setVendorId(vendorId);
            billerCode.setBillerCategory(category);
            billerCode.setCode(dto.getCode());
            billerCode.setDescription(dto.getDescription());
            billerCode.setUpdatedAt(LocalDateTime.now());

            if (billerCode.getId() == null) {
                billerCode.setCreatedAt(LocalDateTime.now());
            }

            billerCodeListRepository.save(billerCode);
        }
    }

    public List<BillerCodeListDto> getBillerCodeDropdown(
            Long vendorId,
            String billerCategoryCode
    ) {
        BillerCategoryList category = billerCategoryListRepository
                .findByVendorIdAndCode(vendorId, billerCategoryCode)
                .orElseThrow(() -> new RuntimeException(
                        "Biller category not found for code: " + billerCategoryCode
                ));

        return billerCodeListRepository
                .findByVendorIdAndBillerCategoryIdOrderByDescriptionAsc(
                        vendorId,
                        category.getId()
                )
                .stream()
                .map(item -> new BillerCodeListDto(
                        item.getCode(),
                        item.getDescription()
                ))
                .toList();
    }

//    @GetMapping("/{vendorId}/biller-codes")
//    public ResponseEntity<?> getBillerCodes(
//            @PathVariable Long vendorId,
//            @RequestParam String categoryCode
//    ) {
//        return ResponseEntity.ok(
//                vimoPayClientService.getBillerCodeDropdown(vendorId, categoryCode)
//        );
//    }
}
