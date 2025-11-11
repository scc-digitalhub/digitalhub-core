package it.smartcommunitylabdhub.framework.argo.config;

import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.log.Log;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.LogService;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.BindException;

@Configuration
@EnableConfigurationProperties({ ApplicationProperties.class, SecurityProperties.class })
@ComponentScan(
    {
        "it.smartcommunitylabdhub.framework.argo",
        "it.smartcommunitylabdhub.commons",
        "it.smartcommunitylabdhub.framework.k8s",
    }
)
public class TestConfiguration {

    @Bean
    protected RunnableStore.StoreSupplier runnableStoreService() {
        return new RunnableStore.StoreSupplier() {
            @Override
            public <T extends RunRunnable> RunnableStore<T> get(Class<T> clazz) {
                return new RunnableStore<T>() {
                    @Override
                    public ResolvableType getResolvableType() {
                        return ResolvableType.forClass(clazz);
                    }

                    @Override
                    public void store(@NotNull String id, @NotNull RunRunnable e) throws StoreException {
                        throw new UnsupportedOperationException("Unimplemented method 'store'");
                    }

                    @Override
                    public void remove(@NotNull String id) throws StoreException {
                        throw new UnsupportedOperationException("Unimplemented method 'remove'");
                    }

                    @Override
                    public T find(@NotNull String id) throws StoreException {
                        throw new UnsupportedOperationException("Unimplemented method 'find'");
                    }

                    @Override
                    public List<T> findAll() {
                        throw new UnsupportedOperationException("Unimplemented method 'findAll'");
                    }
                };
            }
        };
    }

    @Bean
    public LogService getLogService() {
        return new LogService() {
            @Override
            public Page<Log> listLogs(Pageable pageable) throws SystemException {
                return null;
            }

            @Override
            public List<Log> listLogsByUser(@NotNull String user) throws SystemException {
                return java.util.Collections.emptyList();
            }

            @Override
            public List<Log> listLogsByProject(@NotNull String project) throws SystemException {
                return java.util.Collections.emptyList();
            }

            @Override
            public Page<Log> listLogsByProject(@NotNull String project, Pageable pageable) throws SystemException {
                return null;
            }

            @Override
            public List<Log> getLogsByRunId(@NotNull String runId) throws SystemException {
                return java.util.Collections.emptyList();
            }

            @Override
            public Log findLog(@NotNull String id) throws SystemException {
                return null;
            }

            @Override
            public Log getLog(@NotNull String id) throws NoSuchEntityException, SystemException {
                return null;
            }

            @Override
            public Log createLog(@NotNull Log logDTO)
                throws DuplicatedEntityException, BindException, IllegalArgumentException, SystemException {
                return null;
            }

            @Override
            public Log updateLog(@NotNull String id, @NotNull Log logDTO)
                throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException {
                return null;
            }

            @Override
            public void deleteLog(@NotNull String id) throws SystemException {}

            @Override
            public void deleteLogsByProject(@NotNull String project) throws SystemException {}

            @Override
            public void deleteLogsByRunId(@NotNull String runId) throws SystemException {}

            @Override
            public Page<Log> searchLogs(Pageable pageable, SearchFilter<Log> filter) throws SystemException {
                return Page.empty(pageable);
            }

            @Override
            public Page<Log> searchLogsByProject(@NotNull String project, Pageable pageable, SearchFilter<Log> filter)
                throws SystemException {
                return Page.empty(pageable);
            }
        };
    }
}
