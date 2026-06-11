package com.project2.ism.Repository;


import com.project2.ism.Model.Vendor.VendorRates;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public interface VendorRatesRepository extends JpaRepository<VendorRates, Long> {
    Optional<VendorRates> findByProductId(Long productId);

    Long countByEffectiveDateBeforeAndExpiryDateAfter(LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(SUM(vr.monthlyRent),0) " +
            "FROM VendorRates vr " +
            "WHERE vr.effectiveDate <= :today AND vr.expiryDate >= :today")
    BigDecimal sumActiveMonthlyRent(@Param("today") LocalDate today1, @Param("today") LocalDate today2);

    @Query("SELECT vcr.cardType, COUNT(vcr) FROM VendorCardRates vcr GROUP BY vcr.cardType")
    List<Object[]> groupByCardType();

    default Map<String, Long> countGroupByCardType() {
        return groupByCardType().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
    }
}