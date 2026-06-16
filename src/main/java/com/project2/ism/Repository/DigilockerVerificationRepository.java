package com.project2.ism.Repository;

import com.project2.ism.Model.DigilockerVerification;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DigilockerVerificationRepository
        extends JpaRepository<DigilockerVerification, Long> {

    Optional<DigilockerVerification> findByMerchant(Merchant merchant);

    Optional<DigilockerVerification> findByFranchise(Franchise franchise);

    Optional<DigilockerVerification> findByClientId(String clientId);

}
