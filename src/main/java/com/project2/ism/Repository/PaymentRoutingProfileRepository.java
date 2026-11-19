package com.project2.ism.Repository;

import com.project2.ism.Model.Routing.PaymentRoutingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRoutingProfileRepository extends JpaRepository<PaymentRoutingProfile, Long> {

    @Query("SELECT DISTINCT p FROM PaymentRoutingProfile p JOIN FETCH p.product "
            + "LEFT JOIN FETCH p.vendors pv LEFT JOIN FETCH pv.vendor "
            + "WHERE (:productId IS NULL OR p.product.id = :productId) ORDER BY p.id DESC")
    List<PaymentRoutingProfile> findAllWithVendors(@Param("productId") Long productId);

    @Query("SELECT DISTINCT p FROM PaymentRoutingProfile p JOIN FETCH p.product "
            + "LEFT JOIN FETCH p.vendors pv LEFT JOIN FETCH pv.vendor WHERE p.id = :id")
    Optional<PaymentRoutingProfile> findByIdWithVendors(@Param("id") Long id);

    boolean existsByProduct_IdAndNameIgnoreCase(Long productId, String name);

    boolean existsByProduct_IdAndNameIgnoreCaseAndIdNot(Long productId, String name, Long id);

    long countByProduct_Id(Long productId);

    /** Is this vendor used by any routing of this product? (a product-vendor link in use cannot be removed) */
    @Query("SELECT CASE WHEN COUNT(pv) > 0 THEN true ELSE false END FROM PaymentRoutingProfileVendor pv "
            + "WHERE pv.profile.product.id = :productId AND pv.vendor.id = :vendorId")
    boolean vendorUsedInProduct(@Param("productId") Long productId, @Param("vendorId") Long vendorId);
}
