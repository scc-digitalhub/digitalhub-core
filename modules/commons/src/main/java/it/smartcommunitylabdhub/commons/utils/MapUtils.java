package it.smartcommunitylabdhub.commons.utils;

import java.util.*;
import java.util.function.BiFunction;

public class MapUtils {

    private MapUtils() {}

    public static Optional<Map<String, Object>> getNestedFieldValue(Map<String, Object> map, String field) {
        Object value = ((Map<?, ?>) map).get(field);

        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedMap = (Map<String, Object>) value;
            return Optional.of(nestedMap);
        } else {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, Object> computeAndAddElement(Map<String, Object> map, String key, T element) {
        ((ArrayList<T>) map.computeIfAbsent(key, k -> new ArrayList<>())).add(element);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mergeMaps(Map<K, V> map1, Map<K, V> map2, BiFunction<V, V, V> mergeFunction) {
        Map<K, V> mergedMap = new HashMap<>(map1);

        map2.forEach((key, value) ->
            mergedMap.merge(
                key,
                value,
                (oldValue, newValue) -> {
                    if (oldValue instanceof Map && newValue instanceof Map) {
                        // If both values are maps, recursively merge them
                        return (V) mergeMaps((Map<K, V>) oldValue, (Map<K, V>) newValue, mergeFunction);
                    } else if (oldValue instanceof List && newValue instanceof List) {
                        // If both values are lists, concatenate them
                        List<V> mergedList = new ArrayList<>((List<V>) oldValue);
                        mergedList.addAll((List<V>) newValue);
                        return (V) mergedList;
                    } else {
                        // For other types, use the new value
                        return newValue;
                    }
                }
            )
        );

        return mergedMap;
    }

    /**
     * Merge multiple maps in non-recursive mode.
     * If the same key is found in multiple maps, the value from the last map is used.
     * @param maps
     * @return
     * @param <K>
     * @param <V>
     */
    @SafeVarargs
    public static <K, V> Map<K, V> mergeMultipleMaps(Map<K, V>... maps) {
        Map<K, V> mergedMap = new HashMap<>();

        for (Map<K, V> map : maps) {
            if (map != null) mergedMap.putAll(map);
        }
        return mergedMap;
    }

    /**
     * Merge multiple maps in recursive mode.
     * If the same key is found in multiple maps:
     * - If the value is another map, the maps are merged recursively.
     * - Otherwise, the value from the last map is used.
     *
     * @param maps Multiple maps to be merged.
     * @return A single merged map.
     * @param <K> The type of keys maintained by the maps.
     * @param <V> The type of mapped values.
     */
    @SafeVarargs
    public static <K, V> Map<K, V> mergeMultipleMapsRecursively(Map<K, V>... maps) {
        return Arrays.stream(maps)
                .filter(Objects::nonNull)
                .reduce(new HashMap<>(), MapUtils::mergeMapRecursively);
    }

    /**
     * Recursively merges the entries of the source map into the destination map.
     * If a value in both maps is another map, they are merged recursively.
     * Otherwise, the value from the source map overrides the value in the destination map.
     *
     * @param dest The destination map into which entries are merged.
     * @param src The source map from which entries are merged.
     * @param <K> The type of keys maintained by the maps.
     * @param <V> The type of mapped values.
     * @return The merged map.
     */
    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mergeMapRecursively(Map<K, V> dest, Map<K, V> src) {
        src.forEach((key, value) -> {
            if (value instanceof Map && dest.get(key) instanceof Map) {
                Map<K, V> destValue = (Map<K, V>) dest.get(key);
                Map<K, V> srcValue = (Map<K, V>) value;
                dest.put(key, (V) mergeMapRecursively(destValue, srcValue));
            } else if (value instanceof Map) {
                dest.put(key, (V) new HashMap<>((Map<K, V>) value));
            } else {
                dest.put(key, value);
            }
        });
        return dest;
    }
}
