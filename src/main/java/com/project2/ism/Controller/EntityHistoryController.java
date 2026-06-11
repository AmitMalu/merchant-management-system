package com.project2.ism.Controller;

import com.project2.ism.DTO.AdminDTO.EntityHistoryDTO;
import com.project2.ism.DTO.AdminDTO.EntityHistoryPageDTO;
import com.project2.ism.Model.EntityHistory;
import com.project2.ism.Service.EntityHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/history")
public class EntityHistoryController {

    @Autowired
    private EntityHistoryService historyService;

    // Controller - ONE endpoint for everything
    @GetMapping
    public ResponseEntity<EntityHistoryPageDTO> getHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        EntityHistoryPageDTO history = historyService.getHistory(startDate, endDate, page, size);
        return ResponseEntity.ok(history);
    }

    // 2. Get history for a specific entity and its children
    @GetMapping("/entity/{entityName}/{entityId}")
    public ResponseEntity<Map<String, Object>> getEntityHistory(
            @PathVariable String entityName,
            @PathVariable Long entityId
    ) {
        Map<String, Object> result = historyService.getEntityWithChildrenHistory(entityName, entityId);
        return ResponseEntity.ok(result);
    }

    // 3. Get history grouped by timestamp (all changes in one transaction)
    @GetMapping("/grouped")
    public ResponseEntity<List<Map<String, Object>>> getGroupedHistory(
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String parentEntityName,
            @RequestParam(required = false) Long parentEntityId
    ) {
        List<Map<String, Object>> grouped = historyService.getGroupedHistory(
                entityName, entityId, parentEntityName, parentEntityId
        );
        return ResponseEntity.ok(grouped);
    }

    // 4. Get recent activity with pagination
    @GetMapping("/recent")
    public ResponseEntity<EntityHistoryPageDTO> getRecentActivity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        EntityHistoryPageDTO recent = historyService.getRecentHistory(page, size);
        return ResponseEntity.ok(recent);
    }

    // 5. Get activity by user
    @GetMapping("/user/{username}")
    public ResponseEntity<List<EntityHistory>> getUserActivity(
            @PathVariable String username,
            @RequestParam(defaultValue = "100") int limit
    ) {
        List<EntityHistory> userActivity = historyService.getHistoryByUser(username, limit);
        return ResponseEntity.ok(userActivity);
    }
}