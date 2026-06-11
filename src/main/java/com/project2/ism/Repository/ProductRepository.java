package com.project2.ism.Repository;

import com.project2.ism.Model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Find product by product code
     */
    Optional<Product> findByProductCode(String productCode);

    /**
     * Check if product code exists
     */
    boolean existsByProductCode(String productCode);

    /**
     * Find products by vendor ID
     */
    List<Product> findByVendorId(Long vendorId);

    /**
     * Find products by category ID
     */
    List<Product> findByProductCategoryId(Long categoryId);

    /**
     * Find products by status
     */
    List<Product> findByStatusOrderByProductNameAsc(Boolean status);

    /**
     * Find products by brand
     */
    List<Product> findByBrandIgnoreCaseOrderByProductNameAsc(String brand);

    /**
     * Search products by multiple fields
     */
    @Query("SELECT p FROM Product p WHERE " +
            "LOWER(p.productName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.brand) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.model) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Product> searchProducts(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find products by multiple criteria
     */
    @Query("SELECT p FROM Product p WHERE " +
            "(:vendorId IS NULL OR p.vendor.id = :vendorId) AND " +
            "(:brand IS NULL OR LOWER(p.brand) = LOWER(:brand)) AND " +
            "(:categoryId IS NULL OR p.productCategory.id = :categoryId)")
    Page<Product> findProductsByCriteria(@Param("vendorId") Long vendorId,
                                         @Param("brand") String brand,
                                         @Param("categoryId") Long categoryId,
                                         Pageable pageable);

    /**
     * Find products with low stock (assuming you have stock management)
     */
    @Query("SELECT p FROM Product p WHERE p.minOrderQuantity > p.maxOrderQuantity")
    List<Product> findProductsWithInvalidQuantityRange();

    /**
     * Count products by category
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.productCategory.id = :categoryId")
    Long countByProductCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Count products by vendor
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.vendor.id = :vendorId")
    Long countByVendorId(@Param("vendorId") Long vendorId);

    /**
     * Count active products
     */
    Long countByStatus(Boolean status);

    /**
     * Find products by warranty period range
     */
    List<Product> findByWarrantyPeriodBetween(Integer minWarranty, Integer maxWarranty);

    /**
     * Find products created in a date range
     */
    @Query("SELECT p FROM Product p WHERE p.createdAt BETWEEN :startDate AND :endDate")
    List<Product> findByCreatedAtBetween(@Param("startDate") java.time.LocalDateTime startDate,
                                         @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Find distinct brands
     */
    @Query("SELECT DISTINCT p.brand FROM Product p ORDER BY p.brand")
    List<String> findDistinctBrands();

    /**
     * Find distinct models by brand
     */
    @Query("SELECT DISTINCT p.model FROM Product p WHERE LOWER(p.brand) = LOWER(:brand) ORDER BY p.model")
    List<String> findDistinctModelsByBrand(@Param("brand") String brand);
}