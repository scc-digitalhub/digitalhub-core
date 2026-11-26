/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.test;

import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.core.repositories.BaseEntityRepositoryImpl;
import it.smartcommunitylabdhub.core.security.OwnerContextManager;
import it.smartcommunitylabdhub.lifecycle.locks.LocalEntityLockManager;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Minimal persistence context for repository tests: H2 + JPA + auditing + turns + cache
 */
@SpringBootConfiguration
@ImportAutoConfiguration(
    {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class,
    }
)
@EntityScan(basePackageClasses = TestEntity.class)
@EnableJpaRepositories(basePackageClasses = TestJpaRepository.class)
@EnableJpaAuditing(auditorAwareRef = "testAuditor")
public class TestPersistenceConfig {

    public static final Duration LOCK_TIMEOUT = Duration.ofSeconds(1);

    @Bean
    AuditorAware<String> testAuditor() {
        //same logic as production: real user (actor) when acting on behalf of the owner
        return () -> Optional.ofNullable(OwnerContextManager.currentActor());
    }

    @Bean
    LocalEntityLockManager entityLockManager() {
        return new LocalEntityLockManager(LOCK_TIMEOUT);
    }

    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }

    @Bean
    BaseEntityRepositoryImpl<TestEntity, TestDTO> testEntityRepository(TestJpaRepository repository) {
        return new BaseEntityRepositoryImpl<>(
            repository,
            new TestConverters.EntityBuilder(),
            new TestConverters.DtoBuilder()
        ) {};
    }

    @Bean
    EventCollector eventCollector() {
        return new EventCollector();
    }

    /**
     * Collects entity events delivered after commit
     */
    public static class EventCollector {

        private final List<EntityEvent<TestEntity>> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void receive(EntityEvent<TestEntity> event) {
            events.add(event);
        }

        public List<EntityEvent<TestEntity>> getEvents() {
            return events;
        }

        public void clear() {
            events.clear();
        }
    }
}
