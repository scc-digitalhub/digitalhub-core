package it.smartcommunitylabdhub.extensions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

class ExtensionTests {

    @Test
    void schemaLoad() throws Exception {
        DefaultResourceLoader loader = new DefaultResourceLoader();
        Resource entrypoint = loader.getResource("classpath:artifact.json");

        String value = entrypoint.getContentAsString(StandardCharsets.UTF_8);
        Assertions.assertNotNull(value);
    }

    /**
     * Loads a JSON schema and validates a Map<String, Serializable> against it.
     *
     * @param schemaResourcePath the classpath resource path to the JSON schema (e.g., "classpath:artifact.json")
     * @param dataToValidate the map containing data to validate
     * @return a set of validation messages (empty if valid)
     * @throws IOException if the schema cannot be loaded
     * @throws IllegalArgumentException if validation fails
     */
    public Set<ValidationMessage> validateMapAgainstSchema(
        String schemaResourcePath,
        Map<String, Serializable> dataToValidate
    ) throws IOException {
        // Load the JSON schema
        DefaultResourceLoader loader = new DefaultResourceLoader();
        Resource schemaResource = loader.getResource(schemaResourcePath);
        String schemaContent = schemaResource.getContentAsString(StandardCharsets.UTF_8);

        ObjectMapper objectMapper = new ObjectMapper();

        // Parse schema content to JsonNode
        JsonNode schemaNode = objectMapper.readTree(schemaContent);

        // Create JSON schema validator
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        JsonSchema jsonSchema = factory.getSchema(schemaNode);

        // Convert data map to JsonNode for validation
        JsonNode dataNode = objectMapper.valueToTree(dataToValidate);

        // Validate and return validation messages
        return jsonSchema.validate(dataNode);
    }

    @Test
    void validateMapAgainstJsonSchema() throws Exception {
        // Example: validate data against the artifact schema
        Map<String, Serializable> sampleData = Map.of("name", "test-artifact", "kind", "model");

        Set<ValidationMessage> messages = validateMapAgainstSchema("classpath:artifact.json", sampleData);

        // If validation failed, print the messages
        if (!messages.isEmpty()) {
            messages.forEach(msg -> System.out.println("Validation error: " + msg.getMessage()));
        }

        // Assert that validation passed
        Assertions.assertTrue(messages.isEmpty(), "Validation should pass for valid data");
    }
}
