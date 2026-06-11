package com.project2.ism.Repository;


import com.project2.ism.Model.Payment.VendorState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface VendorStateRepository extends JpaRepository<VendorState, Long> {

    List<VendorState> findByVendorId(Long vendorId);

    Optional<VendorState> findByVendorIdAndStateName(Long vendorId, String stateName);
    Optional<VendorState> findByStateName(String stateName);
}
