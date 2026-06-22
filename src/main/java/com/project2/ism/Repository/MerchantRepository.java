package com.project2.ism.Repository;

import com.project2.ism.DTO.ReportDTO.MerchantReportDTO;
import com.project2.ism.Enum.DigilockerStatus;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    List<Merchant> findByFranchiseIsNull();
    List<Merchant> findByFranchiseIsNotNull();
    List<Merchant> findByIsApprovedFalse();

    Optional<Merchant> findByContactPerson_Email(String email);
    @Query("SELECT m.franchise FROM Merchant m WHERE m.id = :merchantId")
    Optional<Franchise> findFranchiseByMerchantId(@Param("merchantId") Long merchantId);

    Long countByFranchiseId(Long id);

    List<Merchant> findByFranchiseId(Long franchiseId);

    @Query("SELECT m FROM Merchant m WHERE m.franchise.id = :franchiseId AND m.status = 'ACTIVE'")
    List<Merchant> findActiveMerchantsByFranchiseId(@Param("franchiseId") Long franchiseId);

    //stats

    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.franchise IS NULL")
    Long countDirectMerchants();

    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.franchise IS NOT NULL")
    Long countFranchiseMerchants();

    @Query("""
    SELECT f.franchiseName, COUNT(m)
    FROM Merchant m
    JOIN m.franchise f
    GROUP BY f.franchiseName
    """)
    List<Object[]> countByFranchise();

    // Sum wallet balance of direct merchants (franchise_id is null)
    @Query("SELECT SUM(m.walletBalance) FROM Merchant m WHERE m.franchise IS NULL")
    BigDecimal sumDirectMerchantWallets();

    // Sum wallet balance of merchants under a franchise (franchise_id not null)
    @Query("SELECT SUM(m.walletBalance) FROM Merchant m WHERE m.franchise IS NOT NULL")
    BigDecimal sumFranchiseMerchantWallets();


    @Query(value = """
    SELECT new com.project2.ism.DTO.ReportDTO.MerchantReportDTO(
        m.businessName,
        COALESCE(f.franchiseName, 'Direct'),
        COALESCE(mw.availableBalance, 0),
        COUNT(DISTINCT psn.id),
        COUNT(DISTINCT ot.product.id),
        m.gstNumber,
        m.panNumber,
        m.registrationNumber,
        m.contactPerson.name,
        m.contactPerson.phoneNumber,
        m.contactPerson.email
    )
    FROM Merchant m
    LEFT JOIN m.franchise f
    LEFT JOIN MerchantWallet mw ON mw.merchant.id = m.id
    LEFT JOIN OutwardTransactions ot ON ot.merchant.id = m.id
    LEFT JOIN ProductSerialNumbers psn ON psn.merchant.id = m.id
    GROUP BY m.id, m.businessName, f.franchiseName, mw.availableBalance,
             m.gstNumber, m.panNumber, m.registrationNumber,
             m.contactPerson.name, m.contactPerson.phoneNumber, m.contactPerson.email
""")
    List<MerchantReportDTO> getMerchantReports();

}
