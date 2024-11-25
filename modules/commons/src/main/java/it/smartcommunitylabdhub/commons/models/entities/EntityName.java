package it.smartcommunitylabdhub.commons.models.entities;

import lombok.Getter;
import lombok.ToString;
import org.springframework.util.Assert;

@Getter
@ToString
public enum EntityName {
    NONE("none"),
    PROJECT("project"),
    WORKFLOW("workflow"),
    FUNCTION("function"),
    SECRET("secret"),
    ARTIFACT("artifact"),
    DATAITEM("dataitem"),
    MODEL("model"),
    TASK("task"),
    TRIGGER("trigger"),
    RUN("run"),
    LOG("log"),
    METADATA("metadata");

    private final String value;

    EntityName(String value) {
        Assert.hasText(value, "value cannot be empty");
        this.value = value;
    }
}
