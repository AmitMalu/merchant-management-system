package com.project2.ism.Repository;

import com.project2.ism.Model.Logs.DigilockerLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DigilockerLogRepository
        extends JpaRepository<DigilockerLog, Long> {

    List<DigilockerLog> findByClientId(String clientId);

    @Query("""
        SELECT d FROM DigilockerLog d
        WHERE (:clientId IS NULL OR d.clientId = :clientId)
        AND (:merchantId IS NULL OR d.merchantId = :merchantId)
        AND (:franchiseId IS NULL OR d.franchiseId = :franchiseId)
        AND (:processStatus IS NULL OR d.processStatus = :processStatus)
        AND (:apiName IS NULL OR d.apiName = :apiName)
        AND (:start IS NULL OR d.createdAt >= :start)
        AND (:end IS NULL OR d.createdAt <= :end)
        ORDER BY d.createdAt DESC
        """)
    Page<DigilockerLog> findLogsByFilters(
            @Param("clientId") String clientId,
            @Param("merchantId") Long merchantId,
            @Param("franchiseId") Long franchiseId,
            @Param("processStatus") String processStatus,
            @Param("apiName") String apiName,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    @Transactional
    void deleteByCreatedAtBefore(LocalDateTime before);

}
