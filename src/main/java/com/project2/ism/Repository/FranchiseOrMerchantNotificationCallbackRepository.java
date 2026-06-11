package com.project2.ism.Repository;

import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationCallback;
import com.project2.ism.Model.Users.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FranchiseOrMerchantNotificationCallbackRepository extends JpaRepository<FranchiseOrMerchantNotificationCallback, Long> {

    Optional<FranchiseOrMerchantNotificationCallback> findByMerchant_Id(Long merchantId);

    Optional<FranchiseOrMerchantNotificationCallback> findByFranchise_Id(Long franchiseId);
}
