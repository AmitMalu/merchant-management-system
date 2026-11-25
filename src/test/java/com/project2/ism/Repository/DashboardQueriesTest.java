package com.project2.ism.Repository;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The admin dashboard queries (methods named dashboard...) are checked by Hibernate's own query parser
 * against the real entities, with no database. A wrong field or entity name would otherwise only show up
 * when the application starts on the server.
 */
class DashboardQueriesTest {

    @Test
    void everyDashboardQueryIsValidHql() throws Exception {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        MetadataSources sources = new MetadataSources(registry);

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        scanner.findCandidateComponents("com.project2.ism.Model")
                .forEach(bd -> {
                    try {
                        sources.addAnnotatedClass(Class.forName(bd.getBeanClassName()));
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                });

        List<String> checked = new ArrayList<>();
        try (SessionFactory factory = sources.buildMetadata().buildSessionFactory();
             EntityManager em = factory.createEntityManager()) {
            // The parser really rejects a wrong field, so a pass above means something.
            assertThrows(Exception.class, () -> em.createQuery("select p.noSuchField from PayoutTransaction p"));

            for (Class<?> repository : List.of(PayoutTransactionRepository.class,
                    BbpsTransactionRepository.class, CreditCardBillTransactionRepository.class)) {
                for (Method method : repository.getDeclaredMethods()) {
                    Query query = method.getAnnotation(Query.class);
                    if (query != null && method.getName().startsWith("dashboard")) {
                        // createQuery parses the text and fails on an unknown field or entity.
                        assertNotNull(em.createQuery(query.value()), repository.getSimpleName() + "." + method.getName());
                        checked.add(repository.getSimpleName() + "." + method.getName());
                    }
                }
            }
        }
        assertEquals(10, checked.size(), "checked: " + checked);
    }
}
