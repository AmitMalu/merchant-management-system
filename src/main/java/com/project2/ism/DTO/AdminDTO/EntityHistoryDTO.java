package com.project2.ism.DTO.AdminDTO;

import java.util.Map;

public class EntityHistoryDTO {
    private String changedAt;
    private String changedBy;
    private String entityName;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String parentEntityName;
    private Map<String, Object> entityDetails;
    private Map<String, Object> parentEntityDetails;

    public EntityHistoryDTO() {
    }

    public EntityHistoryDTO(String changedAt, String changedBy, String entityName,
                            String fieldName, String oldValue, String newValue,
                            String parentEntityName, Map<String, Object> entityDetails,
                            Map<String, Object> parentEntityDetails) {
        this.changedAt = changedAt;
        this.changedBy = changedBy;
        this.entityName = entityName;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.parentEntityName = parentEntityName;
        this.entityDetails = entityDetails;
        this.parentEntityDetails = parentEntityDetails;
    }

    public String getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(String changedAt) {
        this.changedAt = changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getParentEntityName() {
        return parentEntityName;
    }

    public void setParentEntityName(String parentEntityName) {
        this.parentEntityName = parentEntityName;
    }

    public Map<String, Object> getEntityDetails() {
        return entityDetails;
    }

    public void setEntityDetails(Map<String, Object> entityDetails) {
        this.entityDetails = entityDetails;
    }

    public Map<String, Object> getParentEntityDetails() {
        return parentEntityDetails;
    }

    public void setParentEntityDetails(Map<String, Object> parentEntityDetails) {
        this.parentEntityDetails = parentEntityDetails;
    }
}