/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package it.smartcommunitylabhub.commons.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestMergePatch {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TypeReference<HashMap<String, Serializable>> mapType = new TypeReference<>() {};

    private Map<String, Serializable> map(String json) throws Exception {
        return mapper.readValue(json, mapType);
    }

    @Test
    void appliesObjectRootRfcExamples() throws Exception {
        String[][] cases = {
            { "{\"a\":\"b\"}", "{\"a\":\"c\"}", "{\"a\":\"c\"}" },
            { "{\"a\":\"b\"}", "{\"b\":\"c\"}", "{\"a\":\"b\",\"b\":\"c\"}" },
            { "{\"a\":\"b\"}", "{\"a\":null}", "{}" },
            { "{\"a\":\"b\",\"b\":\"c\"}", "{\"a\":null}", "{\"b\":\"c\"}" },
            { "{\"a\":[\"b\"]}", "{\"a\":\"c\"}", "{\"a\":\"c\"}" },
            { "{\"a\":\"c\"}", "{\"a\":[\"b\"]}", "{\"a\":[\"b\"]}" },
            { "{\"a\":{\"b\":\"c\"}}", "{\"a\":{\"b\":\"d\",\"c\":null}}", "{\"a\":{\"b\":\"d\"}}" },
            { "{\"a\":[{\"b\":\"c\"}]}", "{\"a\":[1]}", "{\"a\":[1]}" },
            { "{\"a\":{\"b\":\"c\"}}", "{\"a\":{}}", "{\"a\":{\"b\":\"c\"}}" },
            { "{\"a\":\"b\"}", "{\"a\":{\"b\":null}}", "{\"a\":{}}" },
            { "{}", "{\"a\":{\"bb\":{\"ccc\":null}}}", "{\"a\":{\"bb\":{}}}" },
            { "{\"keep\":null}", "{}", "{\"keep\":null}" },
        };
        for (String[] example : cases) {
            assertEquals(map(example[2]), MapUtils.applyMergePatch(map(example[0]), map(example[1])), example[1]);
        }
    }

    @Test
    void detachesMapsButSharesNonMapLeaves() throws Exception {
        Map<String, Serializable> original = map("{\"untouched\":{\"x\":1},\"nested\":{\"a\":1}}");
        Map<String, Serializable> patch = map("{\"nested\":{\"b\":2},\"new\":{\"c\":3}}");
        ArrayList<String> values = new ArrayList<>();
        values.add("shared");
        original.put("list", values);
        Map<String, Serializable> result = MapUtils.applyMergePatch(original, patch);

        assertNotSame(original, result);
        assertNotSame(original.get("untouched"), result.get("untouched"));
        assertNotSame(original.get("nested"), result.get("nested"));
        assertNotSame(patch.get("new"), result.get("new"));
        assertSame(values, result.get("list"));
        assertEquals(map("{\"untouched\":{\"x\":1},\"nested\":{\"a\":1},\"list\":[\"shared\"]}"), original);
        assertEquals(map("{\"nested\":{\"b\":2},\"new\":{\"c\":3}}"), patch);
    }

    @Test
    void rejectsNullMaps() {
        assertThrows(NullPointerException.class, () -> MapUtils.applyMergePatch(null, Map.of()));
        assertThrows(NullPointerException.class, () -> MapUtils.applyMergePatch(Map.of(), null));
    }

    @Test
    void resultSupportsJavaAndJacksonSerialization() throws Exception {
        Map<String, Serializable> result = MapUtils.applyMergePatch(
            map("{\"nested\":{\"left\":1},\"keep\":null}"),
            map("{\"nested\":{\"right\":2},\"array\":[1,null]}")
        );
        assertEquals(result, map(mapper.writeValueAsString(result)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(result);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(result, in.readObject());
        }
    }
}
