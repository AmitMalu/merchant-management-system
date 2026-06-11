package com.project2.ism.Service;

import com.project2.ism.DTO.AdminDTO.EntityHistoryDTO;
import com.project2.ism.DTO.AdminDTO.EntityHistoryPageDTO;
import com.project2.ism.Model.EntityHistory;
import com.project2.ism.Repository.EntityHistoryRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EntityHistoryService {

    @Autowired
    private EntityHistoryRepository historyRepository;
    @Autowired
    private EntityManager entityManager;

    // Service - One method handles both cases
    public EntityHistoryPageDTO getHistory(LocalDateTime startDate,
                                           LocalDateTime endDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("changedAt").descending());
        Page<EntityHistory> historyPage;

        if (startDate != null || endDate != null) {
            historyPage = historyRepository.findWithFilters(startDate, endDate, pageable);
        } else {
            historyPage = historyRepository.findAll(pageable);
        }

        List<EntityHistoryDTO> dtos = historyPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new EntityHistoryPageDTO(
                dtos,
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements(),
                historyPage.getTotalPages(),
                historyPage.isFirst(),
                historyPage.isLast()
        );
    }

    // Get entity history including its children
    public Map<String, Object> getEntityWithChildrenHistory(String entityName, Long entityId) {
        List<EntityHistory> combined = historyRepository.findByEntityOrParent(entityName, entityId);

        Map<String, Object> result = new HashMap<>();
        result.put("entityName", entityName);
        result.put("entityId", entityId);
        result.put("totalChanges", combined.size());

        // Separate parent changes from child changes
        List<EntityHistory> parentChanges = combined.stream()
                .filter(h -> h.getEntityName().equals(entityName) && h.getEntityId().equals(entityId))
                .collect(Collectors.toList());

        List<EntityHistory> childChanges = combined.stream()
                .filter(h -> entityName.equals(h.getParentEntityName()) && entityId.equals(h.getParentEntityId()))
                .collect(Collectors.toList());

        result.put("parentChanges", parentChanges);
        result.put("childChanges", childChanges);
        result.put("allChanges", combined);

        return result;
    }

    // Get history grouped by timestamp (changes in same transaction)
    public List<Map<String, Object>> getGroupedHistory(String entityName, Long entityId,
                                                       String parentEntityName, Long parentEntityId) {
        List<EntityHistory> history;

        if (entityName != null && entityId != null) {
            history = historyRepository.findByEntityOrParent(entityName, entityId);
        } else if (parentEntityName != null && parentEntityId != null) {
            history = historyRepository.findByParentEntityNameAndParentEntityIdOrderByChangedAtDesc(
                    parentEntityName, parentEntityId
            );
        } else {
            history = historyRepository.findAll();
        }

        // Group by timestamp (rounded to second to catch same-transaction changes)
        Map<String, List<EntityHistory>> grouped = history.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getChangedAt().withNano(0).toString() + "_" + h.getChangedBy(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((key, changes) -> {
            Map<String, Object> group = new HashMap<>();
            group.put("timestamp", changes.get(0).getChangedAt());
            group.put("changedBy", changes.get(0).getChangedBy());
            group.put("changes", changes);
            group.put("changeCount", changes.size());
            result.add(group);
        });

        return result;
    }

    // Get history by user
    public List<EntityHistory> getHistoryByUser(String username, int limit) {
        return historyRepository.findByChangedByOrderByChangedAtDesc(username);
    }


    // Define meaningful fields per entity
    private static final Map<String, List<String>> ENTITY_MEANINGFUL_FIELDS = new HashMap<>();

    static {
        ENTITY_MEANINGFUL_FIELDS.put("CardRate", Arrays.asList("id", "cardName", "rate"));
        ENTITY_MEANINGFUL_FIELDS.put("PricingScheme", Arrays.asList("id", "schemeCode", "description"));
        ENTITY_MEANINGFUL_FIELDS.put("VendorCardRates", Arrays.asList("id", "cardType", "rate"));
        ENTITY_MEANINGFUL_FIELDS.put("VendorRates", Arrays.asList("id", "vendor_id", "product_id","monthlyRent" ,"effectiveDate","expiryDate"));
        // Add more entities as needed
    }

    private Map<String, Object> fetchEntityDetails(String entityName, Long entityId) {
        if (entityId == null || entityName == null) {
            return null;
        }

        try {
            String query = "SELECT e FROM " + entityName + " e WHERE e.id = :id";
            Object entity = entityManager.createQuery(query)
                    .setParameter("id", entityId)
                    .getSingleResult();

            return convertEntityToMap(entity, entityName);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> convertEntityToMap(Object entity, String entityName) {
        if (entity == null) {
            return null;
        }

        // Unproxy if it's a Hibernate proxy
        if (entity instanceof org.hibernate.proxy.HibernateProxy) {
            entity = ((org.hibernate.proxy.HibernateProxy) entity)
                    .getHibernateLazyInitializer()
                    .getImplementation();
        }

        Map<String, Object> map = new LinkedHashMap<>();
        Class<?> clazz = entity.getClass();

        // Get actual class if enhanced by Hibernate
        while (clazz.getName().contains("$")) {
            clazz = clazz.getSuperclass();
        }

        List<String> allowedFields = ENTITY_MEANINGFUL_FIELDS.getOrDefault(
                entityName,
                Arrays.asList("id")
        );

        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            String fieldName = field.getName();

            if (!allowedFields.contains(fieldName)) {
                continue;
            }

            field.setAccessible(true);
            try {
                Object value = field.get(entity);

                if (value == null || value instanceof String || value instanceof Number ||
                        value instanceof Boolean || value instanceof java.util.Date ||
                        value instanceof java.time.LocalDateTime || value instanceof java.time.LocalDate) {
                    map.put(fieldName, value);
                }
            } catch (IllegalAccessException e) {
                // Skip this field
            }
        }

        return map;
    }

    private EntityHistoryDTO toDTO(EntityHistory history) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        Map<String, Object> entityDetails = fetchEntityDetails(
                history.getEntityName(),
                history.getEntityId()
        );

        Map<String, Object> parentEntityDetails = null;
        if (history.getParentEntityName() != null && history.getParentEntityId() != null) {
            parentEntityDetails = fetchEntityDetails(
                    history.getParentEntityName(),
                    history.getParentEntityId()
            );
        }

        return new EntityHistoryDTO(
                history.getChangedAt().format(formatter),
                history.getChangedBy(),
                history.getEntityName(),
                history.getFieldName(),
                history.getOldValue(),
                history.getNewValue(),
                history.getParentEntityName(),
                entityDetails,
                parentEntityDetails
        );
    }

    // Get recent history with pagination
    public EntityHistoryPageDTO getRecentHistory(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("changedAt").descending());
        Page<EntityHistory> historyPage = historyRepository.findAll(pageable);

        List<EntityHistoryDTO> dtos = historyPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new EntityHistoryPageDTO(
                dtos,
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements(),
                historyPage.getTotalPages(),
                historyPage.isFirst(),
                historyPage.isLast()
        );
    }
}