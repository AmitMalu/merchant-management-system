package com.project2.ism.Repository;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The vendor routing queries are written in JPQL, which the application only checks when it starts (and a
 * wrong one stops the server from starting). This parses every one of them against the real entities here,
 * without a database.
 */
class RoutingQueryParsingTest {

    private static SessionFactory sessionFactory;

    @BeforeAll
    static void buildMetamodel() throws Exception {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .build();

        MetadataSources sources = new MetadataSources(registry);
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(MappedSuperclass.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Embeddable.class));
        for (var def : scanner.findCandidateComponents("com.project2.ism")) {
            sources.addAnnotatedClass(Class.forName(def.getBeanClassName()));
        }
        sessionFactory = sources.buildMetadata().buildSessionFactory();
    }

    @AfterAll
    static void close() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    private static final List<Class<?>> ROUTING_REPOSITORIES = List.of(
            PaymentProductVendorRepository.class,
            PaymentRoutingProfileRepository.class,
            PaymentRoutingAssignmentRepository.class,
            PaymentRoutingAuditRepository.class,
            PaymentRoutingTrialRepository.class,
            PaymentAttemptRepository.class,
            PayoutTransactionRepository.class);

    @Test
    void everyRoutingQuery_parsesAgainstTheEntities() {
        int checked = 0;
        for (Class<?> repository : ROUTING_REPOSITORIES) {
            for (Method m : repository.getDeclaredMethods()) {
                Query q = m.getAnnotation(Query.class);
                if (q == null || q.nativeQuery()) {
                    continue;
                }
                try (var session = sessionFactory.openSession()) {
                    assertNotNull(session.createQuery(q.value()), repository.getSimpleName() + "." + m.getName());
                }
                checked++;
            }
        }
        assertFalse(checked == 0, "no query was checked");
    }
}
