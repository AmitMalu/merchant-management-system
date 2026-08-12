package com.project2.ism.Repository;


import com.project2.ism.DTO.BillPayProviderDTO;
import com.project2.ism.Model.Bbps.BillAvenueConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author SHUBHAM KHOPADE
 */
@Repository
public interface BillAvenueConfigRepository extends JpaRepository<BillAvenueConfig, Long> {

    @Query("""
    SELECT DISTINCT bac.serviceName
    FROM BillAvenueConfig bac
    JOIN bac.paymentVendor pv
    WHERE pv.vendorName = :vendorName
      AND pv.status = true
""")
    List<String> findDistinctServicesByVendorName(
            @Param("vendorName") String vendorName);


    @Query("""
    SELECT new com.project2.ism.DTO.BillPayProviderDTO(
        bac.providerId,
        bac.providerName
    )
    FROM BillAvenueConfig bac
    JOIN bac.paymentVendor pv
    WHERE pv.vendorName = :vendorName
      AND pv.status = true
      AND bac.serviceName = :serviceName
""")
    List<BillPayProviderDTO> findProvidersByServiceName(
            @Param("vendorName") String vendorName,
            @Param("serviceName") String serviceName);

}
