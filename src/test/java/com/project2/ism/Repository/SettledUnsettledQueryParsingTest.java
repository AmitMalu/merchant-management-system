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
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Settled / Unsettled report queries are written in JPQL, which the application only checks when it
 * starts. This checks them against the real entity classes here, without a database, so a typo shows up
 * in the tests and not when the server is started.
 */
class SettledUnsettledQueryParsingTest {

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

    private static String queryOf(Class<?> repository, String method) {
        Method m = Arrays.stream(repository.getMethods()).filter(x -> x.getName().equals(method)).findFirst().orElseThrow();
        return m.getAnnotation(Query.class).value();
    }

    private void assertParses(Class<?> repository, String method) {
        String jpql = queryOf(repository, method);
        try (var session = sessionFactory.openSession()) {
            assertNotNull(session.createQuery(jpql), method);
        }
    }

    @Test
    void theSettledUnsettledReportQuery_parses() {
        assertParses(VendorTransactionsRepository.class, "getSettledUnsettledReports");
    }

    @Test
    void theAcquiringBankChoices_parse() {
        assertParses(VendorTransactionsRepository.class, "findDistinctAcquiringBanks");
    }

    @Test
    void theMachineModelQueries_parse() {
        assertParses(ProductSerialsRepository.class, "findModelsByMids");
        assertParses(ProductSerialsRepository.class, "findDistinctModels");
    }
}
