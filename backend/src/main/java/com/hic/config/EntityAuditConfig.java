package com.hic.config;

import org.hibernate.boot.Metadata;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class EntityAuditConfig {
    @Bean
    HibernatePropertiesCustomizer auditEvents() {
        return properties -> properties.put("hibernate.integrator_provider", (IntegratorProvider) () -> List.of(new Integrator() {
            @Override public void integrate(Metadata metadata, SessionFactoryImplementor factory, SessionFactoryServiceRegistry services) {
                var registry = services.getService(EventListenerRegistry.class);
                var listener = new EntityAuditListener();
                registry.appendListeners(EventType.POST_INSERT, listener);
                registry.appendListeners(EventType.POST_UPDATE, listener);
                registry.appendListeners(EventType.POST_DELETE, listener);
            }
            @Override public void disintegrate(SessionFactoryImplementor factory, SessionFactoryServiceRegistry services) {}
        }));
    }
}
