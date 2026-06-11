package com.project2.ism.Repository;

import com.project2.ism.Model.Payment.VendorBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorBankRepository extends JpaRepository<VendorBank, Long> {

    List<VendorBank> findByVendorId(Long vendorId);

    Optional<VendorBank> findByVendorIdAndBankName(Long vendorId, String bankName);

    Optional<VendorBank> findByBankName(String bankName);
}
