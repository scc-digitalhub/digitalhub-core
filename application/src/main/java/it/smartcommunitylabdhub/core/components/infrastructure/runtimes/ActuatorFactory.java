package it.smartcommunitylabdhub.core.components.infrastructure.runtimes;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.ActuatorComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Actuator;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseSpec;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ActuatorFactory {

    private final Map<String, Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus>> actuatorMap;

    public ActuatorFactory(List<Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus>> actuators) {
        actuatorMap =
            actuators.stream().collect(Collectors.toMap(this::getActuatorFromAnnotation, Function.identity()));
    }

    private String getActuatorFromAnnotation(
        Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus> actuator
    ) {
        Class<?> runtimeClass = actuator.getClass();
        if (runtimeClass.isAnnotationPresent(ActuatorComponent.class)) {
            ActuatorComponent annotation = runtimeClass.getAnnotation(ActuatorComponent.class);
            return annotation.actuator();
        }
        throw new IllegalArgumentException(
            "No @ActuatorComponent annotation found for runtime: " + runtimeClass.getName()
        );
    }

    /**
     * Get the Runtime for the given platform.
     *
     * @param runtime The runtime platform
     * @return The Runtime for the specified platform.
     * @throws IllegalArgumentException If no Runtime is found for the given platform.
     */
    public Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus> getActuator(String actuator) {
        Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus> instance = actuatorMap.get(actuator);
        if (instance == null) {
            throw new IllegalArgumentException("No actuator found for name: " + actuator);
        }

        return instance;
    }
}
