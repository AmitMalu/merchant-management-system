package com.project2.ism.Repository;

import com.project2.ism.Model.EntityHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EntityHistoryRepository extends JpaRepository<EntityHistory, Long> {

    // Find by entity
    List<EntityHistory> findByEntityNameAndEntityIdOrderByChangedAtDesc(String entityName, Long entityId);

    // Find by parent entity (includes children)
    List<EntityHistory> findByParentEntityNameAndParentEntityIdOrderByChangedAtDesc(
            String parentEntityName, Long parentEntityId
    );

    // Find by entity OR parent entity (combined view)
    @Query("SELECT eh FROM EntityHistory eh WHERE " +
            "(eh.entityName = :entityName AND eh.entityId = :entityId) OR " +
            "(eh.parentEntityName = :entityName AND eh.parentEntityId = :entityId) " +
            "ORDER BY eh.changedAt DESC")
    List<EntityHistory> findByEntityOrParent(
            @Param("entityName") String entityName,
            @Param("entityId") Long entityId
    );

    // Find by user
    List<EntityHistory> findByChangedByOrderByChangedAtDesc(String changedBy);

    // Recent activity
    List<EntityHistory> findTop50ByOrderByChangedAtDesc();
    // Recent activity with dynamic limit
    List<EntityHistory> findAllByOrderByChangedAtDesc(Pageable pageable);


    // Custom query with multiple filters
    @Query("SELECT eh FROM EntityHistory eh WHERE " +
            "(:startDate IS NULL OR eh.changedAt >= :startDate) AND " +
            "(:endDate IS NULL OR eh.changedAt <= :endDate) " +
            "ORDER BY eh.changedAt DESC")
    Page<EntityHistory> findWithFilters(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );


    @Query("""
        SELECT eh FROM EntityHistory eh 
        WHERE eh.entityName = :entityName 
        AND eh.entityId = :entityId 
        AND eh.fieldName = :fieldName 
        AND eh.changedAt > :transactionDate 
        ORDER BY eh.changedAt ASC
        """)
    List<EntityHistory> findChangesAfterDate(
            @Param("entityName") String entityName,
            @Param("entityId") Long entityId,
            @Param("fieldName") String fieldName,
            @Param("transactionDate") LocalDateTime transactionDate
    );
}