package com.project2.ism.Repository;

import com.project2.ism.Model.Payment.VendorPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface VendorPurposeRepository extends JpaRepository<VendorPurpose, Long> {

    List<VendorPurpose> findByVendorId(Long vendorId);

    Optional<VendorPurpose> findByVendorIdAndPurposeName(Long vendorId, String purposeName);
}
