package it.smartcommunitylabdhub.extensions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.core.specs.SpecRegistryImpl;
import it.smartcommunitylabdhub.extensions.config.ExtensionsProperties;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.extensions.model.ExtensionDefinition;
import it.smartcommunitylabdhub.extensions.model.ExtensionSpec;
import it.smartcommunitylabdhub.extensions.model.SchemaImpl;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ExtensionSchemaService extends SpecRegistryImpl<Extension> {

    public static final long CACHE_TIMEOUT = 30; //seconds
    private static final ObjectMapper objectMapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;
    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<>() {};

    private static final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final String KIND = "kind";
    private static final String SCHEMA = "schema";
    private static final String UI_SCHEMA = "uiSchema";

    protected ResourcePatternResolver resourceLoader;
    private List<String> extensionPaths;

    private EntityService<ExtensionDefinition> extensionService;

    //loading cache
    LoadingCache<String, Schema> extCache = CacheBuilder
        .newBuilder()
        .expireAfterWrite(CACHE_TIMEOUT, TimeUnit.SECONDS)
        .build(
            new CacheLoader<String, Schema>() {
                @Override
                public Schema load(@Nonnull String key) throws Exception {
                    log.debug("reload schema for {}", key);
                    ExtensionDefinition ext = extensionService.find(key);
                    if (ext == null) {
                        log.warn("extension definition not found for {}", key);
                        throw new IllegalArgumentException("extension definition not found for " + key);
                    }

                    return loadSchema(ext);
                }
            }
        );

    @Autowired
    public void setExtensionService(EntityService<ExtensionDefinition> extensionService) {
        this.extensionService = extensionService;
    }

    @Autowired
    public void setResourceLoader(ResourcePatternResolver resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Autowired
    public void setExtensionProperties(ExtensionsProperties extensionProperties) {
        this.extensionPaths = extensionProperties.getPath();
    }

    @Override
    public Schema getSchema(String kind) {
        SpecRegistration reg = registrations.get(kind);
        if (reg != null) {
            return reg.schema();
        }

        try {
            //check for definition matching the kind
            return extCache.get(kind);
        } catch (ExecutionException e) {
            throw new IllegalArgumentException("no schema found for kind " + kind);
        }
    }

    @Override
    public Collection<Schema> listSchemas() {
        List<Schema> result = new ArrayList<>();
        registrations.values().stream().map(e -> e.schema()).forEach(result::add);

        try {
            //pick from db
            extensionService
                .listAll()
                .stream()
                .forEach(def -> {
                    try {
                        Schema s = loadSchema(def);
                        extCache.put(def.getId(), s);
                        result.add(s);
                    } catch (JsonProcessingException e) {
                        log.warn("cannot load schema for extension {}: {}", def.getId(), e.getMessage());
                    }
                });
        } catch (StoreException e) {
            log.error("cannot load extension definitions: {}", e.getMessage());
        }

        return result;
    }

    private Schema loadSchema(ExtensionDefinition ext) throws JsonProcessingException {
        ExtensionSpec spec = ExtensionSpec.from(ext.getSpec());
        JsonNode schemaNode = objectMapper.readTree(spec.getSchema());
        return SchemaImpl.builder().entity("extension").kind(ext.getId()).schema(schemaNode).build();
    }

    public Set<ValidationMessage> validateSchema(@NotNull String kind, @Nullable Map<String, Serializable> map)
        throws IOException {
        log.debug("validate for kind {}", kind);
        if (log.isTraceEnabled()) {
            log.trace("map: {}", map);
        }

        if (!registrations.containsKey(kind)) {
            log.warn("no schema found for kind {}", kind);
            throw new IllegalArgumentException("no schema found for kind " + kind);
        }

        if (map == null) {
            return Set.of();
        }

        // convert schema and build validator
        Schema schema = getSchema(kind);
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
    public Map<String, Serializable> buildSpec(@NotNull String kind, @Nullable Map<String, Serializable> data) {
        log.debug("create spec for kind {}", kind);
        if (log.isTraceEnabled()) {
            log.trace("data: {}", data);
        }

        if (!registrations.containsKey(kind)) {
            log.warn("no schema found for kind {}", kind);
            throw new IllegalArgumentException("no schema found for kind " + kind);
        }

        if (data == null || data.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Schema schema = getSchema(kind);
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

    @Override
    public void afterPropertiesSet() throws Exception {
        //let super scan for java-based registrations
        super.afterPropertiesSet();

        //load from resources
        if (resourceLoader != null && extensionPaths != null) {
            //load all schemas
            for (String path : extensionPaths) {
                if (!StringUtils.hasText(path)) {
                    continue;
                }

                if (!path.endsWith(".json")) {
                    //list all resources in the folder
                    Resource[] resources = resourceLoader.getResources(path + "*.json");
                    for (Resource res : resources) {
                        loadFromResource(res.getURI().toString());
                    }
                } else {
                    loadFromResource(path);
                }
            }
        }
    }

    private void loadFromResource(String path) throws IOException {
        log.debug("loading extension schema from {}", path);
        try {
            Resource res = resourceLoader.getResource(path);
            JsonNode schemaNode = objectMapper.readTree(res.getContentAsString(StandardCharsets.UTF_8));
            String kind = schemaNode.get(KIND).asText();

            SchemaImpl schema = SchemaImpl
                .builder()
                .entity("extension")
                .kind(kind)
                .schema(schemaNode.get(SCHEMA))
                .uiSchema(schemaNode.get(UI_SCHEMA))
                .build();
            registerSpec(kind, schema);

            log.debug("loaded schema for kind {} from {}", kind, path);
        } catch (IOException e) {
            log.error("cannot load extension schema from {}: {}", path, e.getMessage());
        }
    }
}
