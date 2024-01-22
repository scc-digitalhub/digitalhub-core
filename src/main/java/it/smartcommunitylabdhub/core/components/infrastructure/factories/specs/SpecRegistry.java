package it.smartcommunitylabdhub.core.components.infrastructure.factories.specs;

import it.smartcommunitylabdhub.core.annotations.common.SpecType;
import it.smartcommunitylabdhub.core.components.infrastructure.enums.EntityName;
import it.smartcommunitylabdhub.core.components.infrastructure.factories.FactoryUtils;
import it.smartcommunitylabdhub.core.exceptions.CoreException;
import it.smartcommunitylabdhub.core.models.base.interfaces.Spec;
import it.smartcommunitylabdhub.core.utils.ErrorList;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class SpecRegistry<T extends Spec> {
    // A map to store spec types and their corresponding classes.
    private final Map<String, Class<? extends Spec>> specTypes = new HashMap<>();

    private final List<SpecFactory<? extends Spec>> specFactories;

    public SpecRegistry(List<SpecFactory<? extends Spec>> specFactories) {
        this.specFactories = specFactories;
    }


    // Register spec types along with their corresponding classes.
    public void registerSpecTypes(Map<String, Class<? extends Spec>> specTypeMap) {
        specTypes.putAll(specTypeMap);
    }

    /**
     * Create an instance of a spec based on its type and configure it with data.
     *
     * @param kind The type of the spec to create.
     * @param data The data used to configure the spec.
     * @param <S>  The generic type for the spec.
     * @return An instance of the specified spec type, or null if not found or in case of errors.
     */
    public <S extends Spec> S createSpec(@NotNull String kind, @NotNull EntityName entity, Map<String, Object> data) {
        // Retrieve the class associated with the specified spec type.
        final String specKey = kind + "_" + entity.name().toLowerCase();
        return getSpec(data, specKey);
    }

    public <S extends Spec> S createSpec(@NotNull String runtime, @NotNull String kind, @NotNull EntityName entity, Map<String, Object> data) {
        // Retrieve the class associated with the specified spec type.
        final String specKey = runtime + "_" + kind + "_" + entity.name().toLowerCase();
        return getSpec(data, specKey);
    }


    @SuppressWarnings("unchecked")
    private <S extends Spec> S getSpec(Map<String, Object> data, String specKey) {

        Class<? extends T> specClass = (Class<? extends T>) specTypes.get(specKey);

        if (specClass == null) {
            // Fallback spec None if no class specific is found, avoid crash.
            //specClass = (Class<? extends T>) specTypes.get("none_none");
            throw new CoreException(
                    ErrorList.INTERNAL_SERVER_ERROR.getValue(),
                    "Spec not found: tried to extract spec for <" + specKey + "> key",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        try {
            // Find the corresponding SpecFactory using the SpecType annotation.
            SpecType specTypeAnnotation = specClass.getAnnotation(SpecType.class);
            if (specTypeAnnotation != null) {
                Class<?> factoryClass = specTypeAnnotation.factory();
                for (SpecFactory<? extends Spec> specFactory : specFactories) {
                    // Check if the generic type of SpecFactory matches specClass.
                    if (FactoryUtils.isFactoryTypeMatch(factoryClass, specFactory.getClass())) {
                        // Call the create method of the spec factory to get a new instance.
                        S spec = (S) specFactory.create();
                        if (data != null) {
                            spec.configure(data);
                        }
                        return spec;
                    }
                }

            }
            log.error("Cannot configure spec for type @SpecType('" + specKey + "') no way to recover error.");
            throw new CoreException(
                    ErrorList.INTERNAL_SERVER_ERROR.getValue(),
                    "Cannot configure spec for type @SpecType('" + specKey + "')",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            // Handle any exceptions that may occur during instance creation.
            log.error("Cannot configure spec for type @SpecType('" + specKey + "') no way to recover error.");
            throw new CoreException(
                    ErrorList.INTERNAL_SERVER_ERROR.getValue(),
                    "Cannot configure spec for type @SpecType('" + specKey + "')",
                    HttpStatus.INTERNAL_SERVER_ERROR);

        }
    }
}
