package com.project2.ism.Repository;

import com.project2.ism.Model.Routing.PaymentRoutingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRoutingAssignmentRepository extends JpaRepository<PaymentRoutingAssignment, Long> {

    long countByProfile_Id(Long profileId);

    List<PaymentRoutingAssignment> findByProfile_IdOrderByIdDesc(Long profileId);

    @Query("SELECT a FROM PaymentRoutingAssignment a JOIN FETCH a.profile JOIN FETCH a.product "
            + "WHERE a.product.id = :productId AND a.targetType = :type AND a.targetId = :id")
    Optional<PaymentRoutingAssignment> findForAccount(@Param("productId") Long productId,
                                                      @Param("type") String type,
                                                      @Param("id") Long id);

    @Query("SELECT a FROM PaymentRoutingAssignment a WHERE a.product.id = :productId AND a.targetType = :type")
    List<PaymentRoutingAssignment> findByProductAndType(@Param("productId") Long productId,
                                                        @Param("type") String type);

    /** Every routing assignment of one account, with its routing and product (for "which products do I have"). */
    @Query("SELECT a FROM PaymentRoutingAssignment a JOIN FETCH a.profile p JOIN FETCH a.product "
            + "WHERE a.targetType = :type AND a.targetId = :id")
    List<PaymentRoutingAssignment> findAllForAccount(@Param("type") String type, @Param("id") Long id);
}
