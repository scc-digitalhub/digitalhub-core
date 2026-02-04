package it.smartcommunitylabdhub.extensions.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.extensions.config.ExtensionsProperties;
import it.smartcommunitylabdhub.extensions.model.ExtensionSchema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ExtensionSchemaService implements InitializingBean {

    private static final ObjectMapper objectMapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;
    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<>() {};

    private static final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final String KIND = "kind";

    protected ResourceLoader resourceLoader;

    private List<String> extensionPaths;

    private Map<String, Schema> schemas = new LinkedHashMap<>();

    @Autowired
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Autowired
    public void setExtensionProperties(ExtensionsProperties extensionProperties) {
        this.extensionPaths = extensionProperties.getPath();
    }

    public Schema getSchema(@NotNull String kind) {
        if (!schemas.containsKey(kind)) {
            throw new IllegalArgumentException("no schema found for kind " + kind);
        }

        return schemas.get(kind);
    }

    public Set<ValidationMessage> validateSchema(@NotNull String kind, @Nullable Map<String, Serializable> map)
        throws IOException {
        log.debug("validate for kind {}", kind);
        if (log.isTraceEnabled()) {
            log.trace("map: {}", map);
        }

        if (!schemas.containsKey(kind)) {
            log.warn("no schema found for kind {}", kind);
            throw new IllegalArgumentException("no schema found for kind " + kind);
        }

        if (map == null) {
            return Set.of();
        }

        // convert schema and build validator
        Schema schema = schemas.get(kind);
        JsonSchema jsonSchema = factory.getSchema(schema.schema());

        // convert data and validate
        JsonNode node = objectMapper.valueToTree(map);
        return jsonSchema.validate(node);
    }

    /**
     * Creates a spec by filtering the input data to contain only fields defined in the schema.
     * Prunes extra fields at every level of the hierarchical structure based on the schema validation.
     *
     * @param kind the schema kind
     * @param data the input map with potentially extra fields
     * @return a new map containing only fields defined in the schema
     * @throws IllegalArgumentException if no schema is found for the kind
     */
    public Map<String, Serializable> createSpec(@NotNull String kind, @Nullable Map<String, Serializable> data) {
        log.debug("create spec for kind {}", kind);
        if (log.isTraceEnabled()) {
            log.trace("data: {}", data);
        }

        if (!schemas.containsKey(kind)) {
            log.warn("no schema found for kind {}", kind);
            throw new IllegalArgumentException("no schema found for kind " + kind);
        }

        if (data == null || data.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Schema schema = schemas.get(kind);
        JsonNode schemaNode = schema.schema();

        // Convert data to JsonNode for traversal
        JsonNode dataNode = objectMapper.valueToTree(data);

        // Prune the data tree based on schema structure
        JsonNode prunedNode = pruneBySchema(dataNode, schemaNode);

        // Convert back to Map
        Map<String, Serializable> filteredMap = objectMapper.convertValue(prunedNode, typeRef);

        if (log.isTraceEnabled()) {
            log.trace("spec {}: {}", kind, filteredMap);
        }

        return filteredMap;
    }

    /**
     * Prunes a JsonNode to contain only fields defined in the schema.
     * Traverses the entire hierarchical structure and removes keys not in the schema properties.
     *
     * @param dataNode the data to prune
     * @param schemaNode the schema defining valid properties
     * @return pruned JsonNode containing only schema-defined properties
     */
    private JsonNode pruneBySchema(JsonNode dataNode, JsonNode schemaNode) {
        if (!dataNode.isObject()) {
            return dataNode;
        }

        JsonNode propertiesNode = schemaNode.get("properties");
        if (propertiesNode == null || !propertiesNode.isObject()) {
            log.debug("schema has no properties defined");
            return objectMapper.createObjectNode();
        }

        // Build pruned object with only valid properties at this level
        var prunedObject = objectMapper.createObjectNode();
        var dataObject = (com.fasterxml.jackson.databind.node.ObjectNode) dataNode;

        propertiesNode
            .fieldNames()
            .forEachRemaining(fieldName -> {
                if (dataObject.has(fieldName)) {
                    JsonNode fieldValue = dataObject.get(fieldName);
                    JsonNode fieldSchema = propertiesNode.get(fieldName);

                    // Recursively prune nested objects
                    if (fieldValue.isObject() && fieldSchema.has("properties")) {
                        prunedObject.set(fieldName, pruneBySchema(fieldValue, fieldSchema));
                    } else {
                        // For non-object values or values without nested schema, preserve as-is
                        prunedObject.set(fieldName, fieldValue);
                    }
                }
            });

        return prunedObject;
    }

    private String loadSchemaResource(@NonNull String path) throws IOException {
        DefaultResourceLoader loader = new DefaultResourceLoader();
        Resource entrypoint = loader.getResource(path);

        return entrypoint.getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (resourceLoader != null && extensionPaths != null) {
            //flush existing
            schemas = new LinkedHashMap<>();

            //load all schemas
            for (String path : extensionPaths) {
                if (!StringUtils.hasText(path)) {
                    continue;
                }

                log.debug("loading extension schema from {}", path);
                try {
                    String schemaContent = loadSchemaResource(path);
                    JsonNode schemaNode = objectMapper.readTree(schemaContent);
                    String kind = schemaNode.get(KIND).asText();

                    ExtensionSchema schema = ExtensionSchema.builder().kind(kind).schema(schemaNode).build();

                    schemas.put(kind, schema);

                    log.debug("loaded schema for kind {} from {}", kind, path);
                } catch (IOException e) {
                    log.error("cannot load extension schema from {}: {}", path, e.getMessage());
                }
            }
        }
    }
}
