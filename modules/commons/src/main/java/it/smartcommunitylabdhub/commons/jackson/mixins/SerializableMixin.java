package it.smartcommunitylabdhub.commons.jackson.mixins;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    anyOf = {
        SerializableMixin.StringField.class,
        SerializableMixin.NumberField.class,
        // SerializableMixin.IntegerField.class,
        SerializableMixin.BooleanField.class,
        SerializableMixin.SerializableField.class,
        SerializableMixin.SerializableField[].class,
    },
    nullable = true,
    title = "object"
)
public class SerializableMixin {

    @Schema(implementation = String.class, title = "string", defaultValue = "")
    public class StringField {}

    @Schema(implementation = Number.class, title = "number", defaultValue = "")
    public class NumberField {}

    @Schema(implementation = Integer.class, title = "integer", defaultValue = "")
    public class IntegerField {}

    @Schema(implementation = Boolean.class, title = "boolean", defaultValue = "")
    public class BooleanField {}

    @Schema(
        anyOf = {
            SerializableMixin.StringField.class,
            SerializableMixin.NumberField.class,
            // SerializableMixin.IntegerField.class,
            SerializableMixin.BooleanField.class,
            SerializableMixin.SerializableField.class,
            SerializableMixin.SerializableField[].class,
        },
        // nullable = true,
        title = "object"
    )
    public class SerializableField {}
}