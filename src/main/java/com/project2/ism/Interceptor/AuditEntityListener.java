package com.project2.ism.Interceptor;

import com.project2.ism.Service.AuditService;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class AuditEntityListener implements PreUpdateEventListener, PostInsertEventListener, PostDeleteEventListener {

    private static AuditService auditService;

    // Define which entities should be audited (PARENT entities to track)
    private static final Set<String> AUDITED_ENTITIES = new HashSet<>(Arrays.asList(
            "PricingScheme",
            "VendorRates"
            // Add more parent entities you want to audit here
    ));

    // Define which entities are "child entities" that should track INSERT/DELETE
    private static final Set<String> CHILD_ENTITIES = new HashSet<>(Arrays.asList(
            "VendorCardRates",
            "CardRate"
            // Add more child entities here as needed
    ));

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    @Autowired
    public void setAuditService(AuditService auditService) {
        AuditEntityListener.auditService = auditService;
        System.out.println("AuditEntityListener - Spring injected AuditService");
    }

    // ==================== UPDATE EVENT ====================
    @Override
    public boolean onPreUpdate(PreUpdateEvent event) {
        Object entity = event.getEntity();
        String entityName = entity.getClass().getSimpleName();

        // Check if this entity should be audited
        if (!AUDITED_ENTITIES.contains(entityName) && !CHILD_ENTITIES.contains(entityName)) {
            return false; // Skip this entity
        }

        System.out.println("============ AUDIT UPDATE LISTENER TRIGGERED ============");
        System.out.println("Entity: " + entityName);

        try {
            if (auditService == null) {
                System.err.println("ERROR: AuditService is NULL in preUpdate!");
                return false;
            }

            String changedBy = getCurrentUser();
            if (changedBy == null) {
                changedBy = "SYSTEM";
            }

            String entityId = event.getId().toString();

            String[] propertyNames = event.getPersister().getPropertyNames();
            Object[] oldState = event.getOldState();
            Object[] newState = event.getState();

            // Get parent info if this is a child entity
            String parentEntityName = null;
            String parentEntityId = null;
            if (CHILD_ENTITIES.contains(entityName)) {
                Object[] parentInfo = getParentInfo(entity);
                parentEntityName = (String) parentInfo[0];
                parentEntityId = (String) parentInfo[1];
            }

            for (int i = 0; i < propertyNames.length; i++) {
                String fieldName = propertyNames[i];

                if (shouldSkipField(fieldName)) {
                    continue;
                }

                Object oldValue = oldState != null ? oldState[i] : null;
                Object newValue = newState != null ? newState[i] : null;

                if (!areValuesEqual(oldValue, newValue)) {
                    System.out.println("Field: " + fieldName +
                            " changed from [" + oldValue + "] to [" + newValue + "]");

                    auditService.saveHistory(
                            entityName,
                            entityId,
                            fieldName,
                            oldValue != null ? oldValue.toString() : null,
                            newValue != null ? newValue.toString() : null,
                            changedBy,
                            parentEntityName,
                            parentEntityId
                    );
                }
            }

            System.out.println("============ AUDIT UPDATE COMPLETE ============");

        } catch (Exception e) {
            System.err.println("Audit logging failed: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    // ==================== INSERT EVENT (for child entities) ====================
    @Override
    public void onPostInsert(PostInsertEvent event) {
        Object entity = event.getEntity();
        String entityName = entity.getClass().getSimpleName();

        // Only track INSERT for child entities
        if (!CHILD_ENTITIES.contains(entityName)) {
            return;
        }

        System.out.println("============ AUDIT INSERT LISTENER TRIGGERED ============");
        System.out.println("Child Entity INSERTED: " + entityName);

        try {
            if (auditService == null) {
                System.err.println("ERROR: AuditService is NULL in postInsert!");
                return;
            }

            String changedBy = getCurrentUser();
            if (changedBy == null) {
                changedBy = "SYSTEM";
            }

            String entityId = event.getId().toString();

            // Get parent info
            Object[] parentInfo = getParentInfo(entity);
            String parentEntityName = (String) parentInfo[0];
            String parentEntityId = (String) parentInfo[1];

            // Log the insertion as a "RECORD_ADDED" action
            auditService.saveHistory(
                    entityName,
                    entityId,
                    "RECORD_ADDED",
                    null,
                    "New record added",
                    changedBy,
                    parentEntityName,
                    parentEntityId
            );

            System.out.println("============ AUDIT INSERT COMPLETE ============");

        } catch (Exception e) {
            System.err.println("Audit logging for INSERT failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== DELETE EVENT (for child entities) ====================
    @Override
    public void onPostDelete(PostDeleteEvent event) {
        Object entity = event.getEntity();
        String entityName = entity.getClass().getSimpleName();

        // Only track DELETE for child entities
        if (!CHILD_ENTITIES.contains(entityName)) {
            return;
        }

        System.out.println("============ AUDIT DELETE LISTENER TRIGGERED ============");
        System.out.println("Child Entity DELETED: " + entityName);

        try {
            if (auditService == null) {
                System.err.println("ERROR: AuditService is NULL in postDelete!");
                return;
            }

            String changedBy = getCurrentUser();
            if (changedBy == null) {
                changedBy = "SYSTEM";
            }

            String entityId = event.getId().toString();

            // Get parent info
            Object[] parentInfo = getParentInfo(entity);
            String parentEntityName = (String) parentInfo[0];
            String parentEntityId = (String) parentInfo[1];

            // Log the deletion as a "RECORD_DELETED" action
            auditService.saveHistory(
                    entityName,
                    entityId,
                    "RECORD_DELETED",
                    "Record existed",
                    null,
                    changedBy,
                    parentEntityName,
                    parentEntityId
            );

            System.out.println("============ AUDIT DELETE COMPLETE ============");

        } catch (Exception e) {
            System.err.println("Audit logging for DELETE failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== HELPER METHODS ====================

    private Object[] getParentInfo(Object entity) {
        try {
            Field[] fields = entity.getClass().getDeclaredFields();

            for (Field field : fields) {
                if (field.isAnnotationPresent(jakarta.persistence.ManyToOne.class)) {
                    field.setAccessible(true);
                    Object parent = field.get(entity);

                    if (parent != null) {
                        String parentEntityName = parent.getClass().getSimpleName();
                        String parentEntityId = getEntityId(parent);
                        return new Object[]{parentEntityName, parentEntityId};
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to get parent info: " + e.getMessage());
        }

        return new Object[]{null, null};
    }

    private String getEntityId(Object entity) {
        try {
            Field[] fields = entity.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    field.setAccessible(true);
                    Object id = field.get(entity);
                    return id != null ? id.toString() : null;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to get entity ID: " + e.getMessage());
        }
        return null;
    }

    private boolean areValuesEqual(Object oldValue, Object newValue) {
        if (oldValue == null && newValue == null) {
            return true;
        }
        if (oldValue == null || newValue == null) {
            return false;
        }
        return oldValue.equals(newValue);
    }

    private String getCurrentUser() {
        try {
            org.springframework.security.core.Authentication authentication =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            System.err.println("Failed to get user from SecurityContext: " + e.getMessage());
        }

        return null;
    }

    private boolean shouldSkipField(String fieldName) {
        return fieldName.equals("id") ||
                fieldName.equals("version") ||
                fieldName.equals("createdAt") ||
                fieldName.equals("updatedAt") ||
                fieldName.equals("createdBy") ||
                fieldName.equals("updatedBy") ||
                fieldName.equals("vendor") ||
                fieldName.equals("productCategory") ||
                fieldName.equals("product") ||
                fieldName.equals("vendorRate");

    }
}