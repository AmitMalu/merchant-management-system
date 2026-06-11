package com.project2.ism.Service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        System.out.println("AuditService initialized");
    }

    public void saveHistory(String entityName, String entityId, String fieldName,
                            String oldValue, String newValue, String changedBy,
                            String parentEntityName, String parentEntityId) {

        System.out.println("Saving history: " + entityName + "." + fieldName +
                " from [" + oldValue + "] to [" + newValue + "] by " + changedBy +
                (parentEntityName != null ? " (parent: " + parentEntityName + "#" + parentEntityId + ")" : ""));

        try {
            String sql = "INSERT INTO entity_history (entity_name, entity_id, field_name, " +
                    "old_value, new_value, changed_by, changed_at, parent_entity_name, parent_entity_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            int rows = jdbcTemplate.update(sql,
                    entityName,
                    entityId,
                    fieldName,
                    oldValue,
                    newValue,
                    changedBy,
                    LocalDateTime.now(),
                    parentEntityName,
                    parentEntityId
            );

            System.out.println("Audit history saved! Rows affected: " + rows);

        } catch (Exception e) {
            System.err.println("Failed to save audit history: " + e.getMessage());
            e.printStackTrace();
        }
    }
}