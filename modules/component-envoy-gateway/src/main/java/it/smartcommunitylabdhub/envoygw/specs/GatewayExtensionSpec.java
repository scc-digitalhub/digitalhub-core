package it.smartcommunitylabdhub.envoygw.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import it.smartcommunitylabdhub.extensions.model.Extension;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SpecType(kind = GatewayExtensionSpec.KIND, entity = Extension.class)
public class GatewayExtensionSpec extends BaseSpec {

    public static final String KIND = "envoygw";

    @Schema(
        title = "fields.gatewayextension.guardrails.title",
        description = "fields.gatewayextension.guardrails.description"
    )
    private List<String> guardrails;

    // @JsonProperty(value="enabled_payload_logging", required = false, defaultValue = "false")
    // @Schema(title = "fields.gatewayextension.enabledPayloadLogging.title", description = "fields.gatewayextension.enabledPayloadLogging.description")
    // private Boolean enabledPayloadLogging;

    @Override
    public void configure(Map<String, Serializable> data) {
        GatewayExtensionSpec spec = mapper.convertValue(data, GatewayExtensionSpec.class);
        this.guardrails = spec.getGuardrails();
        // this.enabledPayloadLogging = spec.getEnabledPayloadLogging();
    }

    public static GatewayExtensionSpec with(Map<String, Serializable> data) {
        GatewayExtensionSpec spec = new GatewayExtensionSpec();
        spec.configure(data);
        return spec;
    }
}
