package com.project2.ism.Repository;

import com.project2.ism.Model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    /**
     * Find category by name (case-insensitive)
     */
    Optional<ProductCategory> findByCategoryNameIgnoreCase(String categoryName);

    /**
     * Find the category with the highest category code
     * Used for auto-generating new category codes
     */
    Optional<ProductCategory> findTopByOrderByCategoryCodeDesc();

    /**
     * Find all categories ordered by name
     */
    List<ProductCategory> findAllByOrderByCategoryNameAsc();

    /**
     * Find all categories ordered by product count (descending)
     * Useful for statistics
     */
    List<ProductCategory> findAllByOrderByProductCountDesc();

    /**
     * Check if category name exists (case-insensitive)
     */
    boolean existsByCategoryNameIgnoreCase(String categoryName);

    /**
     * Check if category code exists
     */
    boolean existsByCategoryCode(Integer categoryCode);

    /**
     * Find categories with product count greater than specified value
     */
    List<ProductCategory> findByProductCountGreaterThan(Integer count);

    /**
     * Find categories with zero products
     */
    @Query("SELECT pc FROM ProductCategory pc WHERE pc.productCount = 0")
    List<ProductCategory> findEmptyCategories();

    boolean existsByCategoryName(String categoryName);

    Optional<ProductCategory> findByCategoryName(String productCategory);
}