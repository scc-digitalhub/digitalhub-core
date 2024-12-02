package it.smartcommunitylabdhub.core.config;

import it.smartcommunitylabdhub.commons.infrastructure.Actuator;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.base.ExecutableBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseSpec;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseStatus;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerRunBaseStatus;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.core.components.infrastructure.runtimes.ActuatorFactory;
import it.smartcommunitylabdhub.core.components.infrastructure.runtimes.RuntimeFactory;
import it.smartcommunitylabdhub.core.repositories.RunnableRepository;
import it.smartcommunitylabdhub.core.services.RunnableStoreImpl;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(4)
public class InfrastructureConfig {

    @Bean
    protected RuntimeFactory runtimeFactory(
        List<
            Runtime<? extends ExecutableBaseSpec, ? extends RunBaseSpec, ? extends RunBaseStatus, ? extends RunRunnable>
        > runtimes
    ) {
        return new RuntimeFactory(runtimes);
    }

    @Bean
    protected ActuatorFactory actuatorFactory(
        List<Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus, ? extends TriggerRunBaseStatus>> actuators
    ) {
        return new ActuatorFactory(actuators);
    }

    @Bean
    protected RunnableStore.StoreSupplier runnableStoreService(RunnableRepository runnableRepository) {
        return new RunnableStore.StoreSupplier() {
            @Override
            public <T extends RunRunnable> RunnableStore<T> get(Class<T> clazz) {
                return new RunnableStoreImpl<>(clazz, runnableRepository);
            }
        };
    }
}
