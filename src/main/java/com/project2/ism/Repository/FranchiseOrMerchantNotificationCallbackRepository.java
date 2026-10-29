package com.project2.ism.Repository;

import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationCallback;
import com.project2.ism.Model.Users.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FranchiseOrMerchantNotificationCallbackRepository extends JpaRepository<FranchiseOrMerchantNotificationCallback, Long> {

    Optional<FranchiseOrMerchantNotificationCallback> findByMerchant_Id(Long merchantId);

    Optional<FranchiseOrMerchantNotificationCallback> findByFranchise_Id(Long franchiseId);

    // Admin > Other > Notification URL: one row per franchise and per merchant
    boolean existsByFranchise_Id(Long franchiseId);

    boolean existsByMerchant_Id(Long merchantId);

    /** Every row with its franchise / merchant (and the merchant's franchise), most recently changed first. */
    @Query("SELECT c FROM FranchiseOrMerchantNotificationCallback c " +
            "LEFT JOIN FETCH c.franchise " +
            "LEFT JOIN FETCH c.merchant m " +
            "LEFT JOIN FETCH m.franchise " +
            "ORDER BY c.updatedAt DESC, c.id DESC")
    List<FranchiseOrMerchantNotificationCallback> findAllWithOwners();
}
