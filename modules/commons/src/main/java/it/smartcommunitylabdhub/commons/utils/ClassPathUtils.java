package it.smartcommunitylabdhub.commons.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

public class ClassPathUtils {

    public static List<String> getBasePackages(ApplicationContext applicationContext) {
        Set<String> basePackages = new HashSet<>();

        // Get all configuration classes
        String[] beanNames = applicationContext.getBeanNamesForAnnotation(SpringBootApplication.class);
        if (beanNames.length == 0) {
            beanNames = applicationContext.getBeanNamesForAnnotation(ComponentScan.class);
        }

        for (String beanName : beanNames) {
            Class<?> configClass = applicationContext.getType(beanName);
            if (configClass != null) {
                ComponentScan componentScan = configClass.getAnnotation(ComponentScan.class);
                if (componentScan != null && componentScan.basePackages().length > 0) {
                    Collections.addAll(basePackages, componentScan.basePackages());
                }
                // Also check for value attribute
                if (componentScan != null && componentScan.value().length > 0) {
                    Collections.addAll(basePackages, componentScan.value());
                }
                // If basePackages is empty, use the package of the config class itself
                if (basePackages.isEmpty() && componentScan != null) {
                    basePackages.add(configClass.getPackage().getName());
                }
            }
        }

        if (basePackages.isEmpty()) {
            throw new IllegalArgumentException("Base package not specified in @ComponentScan");
        }

        return Collections.unmodifiableList(new ArrayList<>(basePackages));
    }

    private ClassPathUtils() {
        // private constructor for utils
    }
}
