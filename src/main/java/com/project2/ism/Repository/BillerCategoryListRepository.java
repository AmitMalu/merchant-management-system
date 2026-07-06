package com.project2.ism.Repository;


import com.project2.ism.Model.Bbps.BillerCategoryList;
import com.project2.ism.Model.Payment.VendorPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillerCategoryListRepository extends JpaRepository<BillerCategoryList, Long> {

    Optional<BillerCategoryList> findByVendorIdAndCode(Long vendorId, String code);

}
