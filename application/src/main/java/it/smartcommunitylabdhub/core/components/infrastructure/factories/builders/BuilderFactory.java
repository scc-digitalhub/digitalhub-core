/**
 * BuilderFactory.java
 * <p>
 * This class is a factory for managing and providing Builders (builders).
 */

package it.smartcommunitylabdhub.core.components.infrastructure.factories.builders;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.BuilderComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Builder;
import it.smartcommunitylabdhub.commons.models.base.ExecutableBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.task.TaskBaseSpec;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BuilderFactory {

    private final Map<
        String,
        ? extends Builder<? extends ExecutableBaseSpec, ? extends TaskBaseSpec, ? extends RunBaseSpec>
    > builderMap;

    /**
     * Constructor to create the BuilderFactory with a list of Builders.
     *
     * @param builders The list of Builders to be managed by the factory.
     */
    public BuilderFactory(
        List<? extends Builder<? extends ExecutableBaseSpec, ? extends TaskBaseSpec, ? extends RunBaseSpec>> builders
    ) {
        builderMap = builders.stream().collect(Collectors.toMap(this::getBuilderFromAnnotation, Function.identity()));
    }

    /**
     * Get the platform string from the @BuilderComponent annotation for a given Builder.
     *
     * @param builder The Builder for which to extract the platform string.
     * @return The platform string extracted from the @BuilderComponent annotation.
     * @throws IllegalArgumentException If no @BuilderComponent annotation is found for the
     *                                  builder.
     */
    private <
        B extends Builder<? extends ExecutableBaseSpec, ? extends TaskBaseSpec, ? extends RunBaseSpec>
    > String getBuilderFromAnnotation(B builder) {
        Class<?> builderClass = builder.getClass();
        if (builderClass.isAnnotationPresent(BuilderComponent.class)) {
            BuilderComponent annotation = builderClass.getAnnotation(BuilderComponent.class);
            return annotation.runtime() + "+" + annotation.task();
        }
        throw new IllegalArgumentException(
            "No @BuilderComponent annotation found for builder: " + builderClass.getName()
        );
    }

    /**
     * Get the Builder for the given platform.
     *
     * @param runtime The builder platform
     * @param task    The task
     * @return The Builder for the specified platform.
     * @throws IllegalArgumentException If no Builder is found for the given platform.
     */
    @SuppressWarnings("unchecked")
    public <
        B extends Builder<? extends ExecutableBaseSpec, ? extends TaskBaseSpec, ? extends RunBaseSpec>
    > B getBuilder(String runtime, String task) {
        B concreteBuilder = (B) builderMap.get(runtime + "+" + task);
        if (concreteBuilder == null) {
            throw new IllegalArgumentException("No builder found for name: " + runtime + "+" + task);
        }
        return concreteBuilder;
    }

    public Map<
        String,
        ? extends Builder<? extends ExecutableBaseSpec, ? extends TaskBaseSpec, ? extends RunBaseSpec>
    > getBuilders(String runtime) {
        return builderMap
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey().startsWith(runtime))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
