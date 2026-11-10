package com.project2.ism.Repository;

import com.project2.ism.Model.Bbps.BbpsComplaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BbpsComplaintRepository extends JpaRepository<BbpsComplaint, Long> {

    Optional<BbpsComplaint> findByComplaintIdAndMerchantId(String complaintId, Long merchantId);

    List<BbpsComplaint> findTop50ByMerchantIdOrderByCreatedAtDesc(Long merchantId);
}
