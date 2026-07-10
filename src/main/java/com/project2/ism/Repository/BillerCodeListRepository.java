package com.project2.ism.Repository;

import com.project2.ism.Model.Bbps.BillerCategoryList;
import com.project2.ism.Model.Bbps.BillerCodeList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillerCodeListRepository extends JpaRepository<BillerCodeList, Long> {

    Optional<BillerCodeList> findByVendorIdAndBillerCategoryIdAndCode(
            Long vendorId,
            Long billerCategoryId,
            String code
    );

    List<BillerCodeList> findByVendorIdAndBillerCategoryIdOrderByDescriptionAsc(
            Long vendorId,
            Long billerCategoryId
    );

}
