package it.smartcommunitylabdhub.commons.annotations.common;

import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import org.springframework.stereotype.Indexed;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Indexed
public @interface RunProcessorType {
    String[] stages(); // onRunning onCompleted....

    String id();
}
