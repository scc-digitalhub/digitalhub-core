package it.smartcommunitylabdhub.framework.k8s.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = QuantityJacksonDeserializer.class)
public class QuantityMixin {
}
