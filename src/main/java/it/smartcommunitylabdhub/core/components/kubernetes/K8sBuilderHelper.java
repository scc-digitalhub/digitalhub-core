package it.smartcommunitylabdhub.core.components.kubernetes;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1ConfigMapEnvSource;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1SecretEnvSource;
import io.kubernetes.client.openapi.models.V1SecretKeySelector;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper class for building Kubernetes  environment variables.
 * This class provides methods to retrieve environment variables with fallback values
 * and constructs a list of V1EnvVar objects for use in Kubernetes  specifications.
 */
@Component
public class K8sBuilderHelper {

    @Value("${application.endpoint}")
    private String DH_ENDPOINT;

    @Value("${kubernetes.config.secret}")
    private List<String> SECRET;

    @Value("${kubernetes.config.config-map}")
    private List<String> CONFIG_MAP;


    /**
     * A helper method to get an environment variable with a default value if not present.
     *
     * @param variableName The name of the environment variable.
     * @param defaultValue The default value to use if the environment variable is not present.
     * @return The value of the environment variable if present, otherwise the defaultValue.
     */
    private String getEnvVariable(String variableName, String defaultValue) {
        // Access the environment variable using System.getenv()
        String value = System.getenv(variableName);

        System.getenv();
        // Use the value from the system environment if available, otherwise use the defaultValue
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }


    /**
     * Retrieve the dh end point variable
     *
     * @return V1EnvVar
     */
    public List<V1EnvVar> getV1EnvVar() {
        //                v1EnvVars.add(
        //                new V1EnvVar().name("DHUB_CORE_ENDPOINT")
        //                        .value(DH_ENDPOINT));
        return new ArrayList<>();
    }

    /**
     * Method to retrieve a list of V1EnvFromSource containing environment variables for a Kubernetes .
     * It retrieve env from Config Map and Secret
     *
     * @return A list of V1EnvVar objects representing environment variables for a Kubernetes .
     */

    public List<V1EnvFromSource> getV1EnvFromSource() {

        // Get Env var from secret and config map
        return Stream.concat(
                CONFIG_MAP.stream().map(value -> new V1EnvFromSource().configMapRef(
                        new V1ConfigMapEnvSource().name(value)
                )),
                SECRET.stream()
                        //.filter(secret -> !secret.equals("")) // skip postgres
                        .map(secret -> new V1EnvFromSource().secretRef(
                                new V1SecretEnvSource().name(secret)
                        ))
        ).toList();

    }

    public List<V1EnvVar> geEnvVarsFromSecrets(Map<String, Set<String>> secrets) {
        if (secrets != null) {
            return secrets.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .flatMap(entry -> entry.getValue().stream()
                        .map(key -> new V1EnvVar()
                                    .name(key)
                                    .valueFrom(new V1EnvVarSource().secretKeyRef(new V1SecretKeySelector().name(entry.getKey()).key(key))))
                )
                .toList();
        }
        return Collections.emptyList();
    }


    public V1Volume getVolume(Map<String, Object> map) {
        V1Volume volume = new V1Volume();
        String type = (String)map.getOrDefault("volume_type", "");
        map.remove("volume_type");
        switch (type) {
            case "config_map": return volume.configMap(new ObjectMapper().convertValue(map, V1ConfigMapVolumeSource.class));        
            case "secret": return volume.secret(new ObjectMapper().convertValue(map, V1SecretVolumeSource.class));        
            case "persistent_volume_claim": return volume.persistentVolumeClaim(new ObjectMapper().convertValue(map, V1PersistentVolumeClaimVolumeSource.class));        
            default: return null;
        }
    }


    public Map<String, Quantity> convertResources(Map<String, String> map) {
        return map.entrySet().stream()
        .map(entry -> Map.entry(entry.getKey(), Quantity.fromString(entry.getValue())))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

}