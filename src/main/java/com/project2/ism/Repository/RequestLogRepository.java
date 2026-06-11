package com.project2.ism.Repository;

import com.project2.ism.Model.RequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    // Find logs by user ID
    List<RequestLog> findByUserIdOrderByCreatedAtDesc(String userId);

    // Find logs by username
    List<RequestLog> findByUsernameOrderByCreatedAtDesc(String username);

    // Find logs by request method
    List<RequestLog> findByRequestMethodOrderByCreatedAtDesc(String requestMethod);

    // Find logs by response status
    List<RequestLog> findByResponseStatusOrderByCreatedAtDesc(Integer responseStatus);

    // Find logs within date range
    List<RequestLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // Find logs by user and date range
    List<RequestLog> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String userId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // Find error logs (status >= 400)
    @Query("SELECT rl FROM RequestLog rl WHERE rl.responseStatus >= 400 ORDER BY rl.createdAt DESC")
    List<RequestLog> findErrorLogs();

    // Find slow requests (execution time > specified ms)
    @Query("SELECT rl FROM RequestLog rl WHERE rl.executionTimeMs > :executionTime ORDER BY rl.executionTimeMs DESC")
    List<RequestLog> findSlowRequests(@Param("executionTime") Long executionTime);

    // Count requests by user
//    @Query("SELECT COUNT(rl) FROM RequestLog rl WHERE rl.userId = :userId")
//    Long countRequestsByUserId(@Param("userId") String userId);
//
//    // Find logs with pagination
//    Page<RequestLog> findByOrderByCreatedAtDesc(Pageable pageable);
//
//    // Find user logs with pagination
//    Page<RequestLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    // Custom search query
//    @Query("SELECT rl FROM RequestLog rl WHERE " +
//            "(:userId IS NULL OR rl.userId = :userId) AND " +
//            "(:method IS NULL OR rl.requestMethod = :method) AND " +
//            "(:status IS NULL OR rl.responseStatus = :status) AND " +
//            "(:startDate IS NULL OR rl.createdAt >= :startDate) AND " +
//            "(:endDate IS NULL OR rl.createdAt <= :endDate) " +
//            "ORDER BY rl.createdAt DESC")
//    Page<RequestLog> findLogsByFilters(
//            @Param("userId") String userId,
//            @Param("method") String method,
//            @Param("status") Integer status,
//            @Param("startDate") LocalDateTime startDate,
//            @Param("endDate") LocalDateTime endDate,
//            Pageable pageable
//    );

    // Delete old logs
   // void deleteByCreatedAtBefore(LocalDateTime cutoffDate);

    List<RequestLog> findByRequestUrlContainingOrderByCreatedAtDesc(String urlPart);

    List<RequestLog> findByRequestMethodAndResponseStatusBetweenOrderByCreatedAtDesc(
            String method, Integer startStatus, Integer endStatus);

    List<RequestLog> findByResponseStatusGreaterThanEqualOrderByCreatedAtDesc(Integer status);

    List<RequestLog> findByRequestUrlContainingAndResponseStatusBetweenOrderByCreatedAtDesc(
            String urlPart, Integer startStatus, Integer endStatus);

    Page<RequestLog> findByOrderByCreatedAtDesc(Pageable pageable);

    Page<RequestLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<RequestLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<RequestLog> findByResponseStatusOrderByCreatedAtDesc(Integer status, Pageable pageable);

    Page<RequestLog> findByResponseStatusGreaterThanEqualOrderByCreatedAtDesc(Integer status, Pageable pageable);

    Page<RequestLog> findByExecutionTimeMsGreaterThanEqualOrderByCreatedAtDesc(Long thresholdMs, Pageable pageable);

    Long countRequestsByUserId(String userId);

    Long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    void deleteByCreatedAtBefore(LocalDateTime before);

    @Query("""
        SELECT r FROM RequestLog r 
        WHERE (:userId IS NULL OR r.userId = :userId)
        AND (:method IS NULL OR r.requestMethod = :method)
        AND (:status IS NULL OR r.responseStatus = :status)
        AND (:start IS NULL OR r.createdAt >= :start)
        AND (:end IS NULL OR r.createdAt <= :end)
        ORDER BY r.createdAt DESC
        """)
    Page<RequestLog> findLogsByFilters(@Param("userId") String userId,
                                       @Param("method") String method,
                                       @Param("status") Integer status,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end,
                                       Pageable pageable);
}
