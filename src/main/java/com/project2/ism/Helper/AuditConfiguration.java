package com.project2.ism.Helper;

import com.project2.ism.Interceptor.AuditEntityListener;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditConfiguration {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private AuditEntityListener auditEntityListener;

    @PostConstruct
    public void registerListeners() {
        SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);
        EventListenerRegistry registry = sessionFactory.getServiceRegistry()
                .getService(EventListenerRegistry.class);

        registry.appendListeners(EventType.PRE_UPDATE, auditEntityListener);

        // Register for INSERT (for child entities)
        registry.appendListeners(EventType.POST_INSERT, auditEntityListener);

        // Register for DELETE (for child entities)
        registry.appendListeners(EventType.POST_DELETE, auditEntityListener);


    }
}