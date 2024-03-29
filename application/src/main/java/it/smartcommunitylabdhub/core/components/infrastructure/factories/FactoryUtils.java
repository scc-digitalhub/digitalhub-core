package it.smartcommunitylabdhub.core.components.infrastructure.factories;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class FactoryUtils {

    /**
     * Checks if the generic type argument of a given SpecFactory matches a specified factoryClass.
     *
     * @param factoryClass     The factory class to be checked against the SpecFactory.
     * @param specFactoryClass The SpecFactory class containing the generic type argument.
     * @return True if the generic type argument of SpecFactory matches factoryClass;
     * otherwise, false.
     */
    public static Boolean isFactoryTypeMatch(Class<?> factoryClass, Class<?> specFactoryClass) {
        // Check if the generic type argument of SpecFactory matches specClass.
        Type[] interfaceTypes = specFactoryClass.getGenericInterfaces();
        for (Type type : interfaceTypes) {
            if (type instanceof ParameterizedType parameterizedType) {
                Type[] typeArguments = parameterizedType.getActualTypeArguments();
                if (typeArguments.length == 1 && typeArguments[0] instanceof Class<?> factoryTypeArgument) {
                    return factoryTypeArgument.isAssignableFrom(factoryClass);
                }
            }
        }
        return false;
    }

    /**
     * Check if the generic type argument of SpecFactory matches specClass.
     *
     * @param paramClass     the factory class
     * @param actualClass the spec factory class
     * @return true if the type matches, false otherwise
     */
    public static Boolean isParamTypeMatch(Class<?> actualClass, Class<?> paramClass, int pos) {
        // Check if the generic type argument of SpecFactory matches specClass.
        Type[] interfaceTypes = actualClass.getGenericInterfaces();
        if (interfaceTypes.length > pos) {
            Type type = interfaceTypes[pos];

            if (type instanceof ParameterizedType parameterizedType) {
                Type[] typeArguments = parameterizedType.getActualTypeArguments();
                if (typeArguments.length == 1 && typeArguments[0] instanceof Class) {
                    // Case: SpecFactory<TypeX>
                    if (paramClass.isAssignableFrom((Class<?>) typeArguments[0])) {
                        return true;
                    }
                } else if (typeArguments.length == 1 && typeArguments[0] instanceof ParameterizedType) {
                    // Case: SpecFactory<GenericTypeX<T>>
                    Type rawTypeArgument = ((ParameterizedType) typeArguments[0]).getRawType();
                    if (rawTypeArgument.equals(paramClass) || paramClass.isAssignableFrom((Class<?>) rawTypeArgument)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
