package it.smartcommunitylabhub.commons.utils;

import it.smartcommunitylabdhub.commons.utils.MapUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;



public class TestMapUtils {

    @Test
    public void testMergeMultipleMaps() {
        // Sample maps for testing
        Map<String, Object> map1 = new HashMap<>();
        map1.put("a", 1);
        map1.put("b", Map.of("x", 10, "y", 20));

        Map<String, Object> map2 = new HashMap<>();
        map2.put("b", Map.of("y", 30, "z", 40));
        map2.put("c", 3);

        Map<String, Object> map3 = new HashMap<>();
        map3.put("a", 2);
        map3.put("b", Map.of("x", 15));

        // Expected result
        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("a", 2);
        expectedResult.put("b", Map.of("x", 15, "y", 30, "z", 40));
        expectedResult.put("c", 3);

        // Merge the maps
        Map<String, Object> result = MapUtils.mergeMultipleMapsRecursively(map1, map2, map3);

        // Assert the merged result matches the expected result
        Assertions.assertEquals(expectedResult, result);
    }

    @Test
    public void testMergeWithNestedMaps() {
        // Sample maps with nested maps for testing
        Map<String, Object> map1 = new HashMap<>();
        map1.put("a", Map.of("x", 1, "y", 2));

        Map<String, Object> map2 = new HashMap<>();
        map2.put("a", Map.of("y", 3, "z", 4));

        // Expected result
        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("a", Map.of("x", 1, "y", 3, "z", 4));

        // Merge the maps
        Map<String, Object> result = MapUtils.mergeMultipleMapsRecursively(map1, map2);

        // Assert the merged result matches the expected result
        Assertions.assertEquals(expectedResult, result);
    }

    @Test
    public void testMergeEmptyMaps() {
        // Merge empty maps
        Map<String, Object> result = MapUtils.mergeMultipleMapsRecursively();

        // Assert the result is an empty map
        Assertions.assertTrue(result.isEmpty());
    }
}
