/**
 * RuntimeFactory.java
 * <p>
 * This class is a factory for managing and providing Runtimes (runtimes).
 */

package it.smartcommunitylabdhub.core.components.infrastructure.factories.runtimes;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Runnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.entities.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RuntimeFactory {

    private final Map<
            String,
            Runtime<? extends FunctionBaseSpec, ? extends RunBaseSpec, ? extends RunBaseStatus, ? extends Runnable>
            > runtimeMap;

    /**
     * Constructor to create the RuntimeFactory with a list of Runtimes.
     *
     * @param runtimes The list of Runtimes to be managed by the factory.
     */
    public RuntimeFactory(
            List<Runtime<? extends FunctionBaseSpec, ? extends RunBaseSpec, ? extends RunBaseStatus, ? extends Runnable>> runtimes
    ) {
        runtimeMap = runtimes.stream().collect(Collectors.toMap(this::getRuntimeFromAnnotation, Function.identity()));
    }

    /**
     * Get the platform string from the @RuntimeComponent annotation for a given Runtime.
     *
     * @param runtime The Runtime for which to extract the platform string.
     * @return The platform string extracted from the @RuntimeComponent annotation.
     * @throws IllegalArgumentException If no @RuntimeComponent annotation is found for the
     *                                  runtime.
     */
    private String getRuntimeFromAnnotation(
            Runtime<? extends FunctionBaseSpec, ? extends RunBaseSpec, ? extends RunBaseStatus, ? extends Runnable> runtime
    ) {
        Class<?> runtimeClass = runtime.getClass();
        if (runtimeClass.isAnnotationPresent(RuntimeComponent.class)) {
            RuntimeComponent annotation = runtimeClass.getAnnotation(RuntimeComponent.class);
            return annotation.runtime();
        }
        throw new IllegalArgumentException(
                "No @RuntimeComponent annotation found for runtime: " + runtimeClass.getName()
        );
    }

    /**
     * Get the Runtime for the given platform.
     *
     * @param runtime The runtime platform
     * @return The Runtime for the specified platform.
     * @throws IllegalArgumentException If no Runtime is found for the given platform.
     */
    public Runtime<? extends FunctionBaseSpec, ? extends RunBaseSpec, ? extends RunBaseStatus, ? extends Runnable> getRuntime(String runtime) {
        Runtime<? extends FunctionBaseSpec, ? extends RunBaseSpec, ? extends RunBaseStatus, ? extends Runnable> concreteRuntime = runtimeMap.get(
                runtime
        );
        if (concreteRuntime == null) {
            throw new IllegalArgumentException("No runtime found for name: " + runtime);
        }
        return concreteRuntime;
    }
}
