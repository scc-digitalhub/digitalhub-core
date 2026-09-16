/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.OnnxModelSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TfliteModelSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmIrModelSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmSoModelSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeTaskSpec;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// Every TVM spec declares a uiSchema that labels, in English and Italian, each field the
// runtime adds: a new field without a label fails here.
class TvmUiSchemaTest {

    private static final String TVM_PACKAGE = "it.smartcommunitylabdhub.runtime.tvm";

    private static final List<Class<?>> SPECS = List.of(
        TvmFunctionSpec.class,
        TvmBuildTaskSpec.class,
        TvmCompileTaskSpec.class,
        TvmServeTaskSpec.class,
        TvmBuildRunSpec.class,
        TvmCompileRunSpec.class,
        TvmServeRunSpec.class,
        OnnxModelSpec.class,
        TfliteModelSpec.class,
        TvmIrModelSpec.class,
        TvmSoModelSpec.class
    );

    @Test
    void labelsEveryFieldInEnglishAndItalian() throws Exception {
        for (Class<?> spec : SPECS) {
            assertLabels(spec);
        }
    }

    private void assertLabels(Class<?> spec) throws Exception {
        String location = spec.getAnnotation(SpecType.class).uiSchema();
        assertTrue(!location.isEmpty(), spec.getSimpleName() + " declares no uiSchema");

        JsonNode ui;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(location)) {
            assertNotNull(stream, "missing uiSchema " + location);
            ui = new ObjectMapper().readTree(stream);
        }
        assertLabelled(ui, location);
        for (String property : tvmProperties(spec)) {
            assertTrue(ui.has(property), location + " has no label for " + property);
            assertLabelled(ui.get(property), location + "#" + property);
        }
    }

    private static void assertLabelled(JsonNode node, String where) {
        for (String key : List.of("ui:title@en", "ui:description@en", "ui:title@it", "ui:description@it")) {
            assertTrue(node.hasNonNull(key) && !node.get(key).asText().isBlank(), where + " misses " + key);
        }
    }

    // JSON properties declared by the TVM classes of the spec, unwrapped specs included.
    private static List<String> tvmProperties(Class<?> type) {
        List<String> properties = new ArrayList<>();
        for (
            Class<?> current = type;
            current != null && current.getName().startsWith(TVM_PACKAGE);
            current = current.getSuperclass()
        ) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(JsonUnwrapped.class)) {
                    properties.addAll(tvmProperties(field.getType()));
                }
                JsonProperty property = field.getAnnotation(JsonProperty.class);
                if (property != null) {
                    properties.add(property.value());
                }
            }
        }
        return properties;
    }
}
