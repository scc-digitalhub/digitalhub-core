/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.framework.k8s.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretKeySelector;
import io.kubernetes.client.util.PatchUtils;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@ConditionalOnKubernetes
public class K8sSecretHelper {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final CoreV1Api api;

    @Value("${kubernetes.namespace}")
    private String namespace;

    @Value("${kubernetes.config.secret}")
    private List<String> sharedSecrets;

    public K8sSecretHelper(ApiClient client) {
        api = new CoreV1Api(client);
    }

    public List<V1EnvVar> getV1EnvVar() {
        List<V1EnvVar> vars = new ArrayList<>();

        //add shared secrets
        if (sharedSecrets != null) {
            sharedSecrets
                .stream()
                .forEach(s -> {
                    try {
                        Map<String, String> data = getSecretData(s);
                        if (data != null) {
                            data.forEach((key, v) ->
                                vars.add(
                                    //add as reference
                                    new V1EnvVar()
                                        .name(key)
                                        .valueFrom(
                                            new V1EnvVarSource()
                                                .secretKeyRef(new V1SecretKeySelector().name(s).key(key))
                                        )
                                )
                            );
                        }
                    } catch (ApiException e) {
                        //catch and skip this container's logs
                        log.error("Error with k8s: {}", e.getMessage());
                        if (log.isTraceEnabled()) {
                            log.trace("k8s api response: {}", e.getResponseBody());
                        }
                    }
                });
        }

        return vars;
    }

    public Map<String, String> getSecretData(String secretName) throws ApiException {
        V1Secret secret = api.readNamespacedSecret(secretName, namespace, "");
        if (secret != null) {
            Map<String, byte[]> secretData = secret.getData();
            Map<String, String> writeData = new HashMap<>();

            if (secretData != null) {
                for (String key : secretData.keySet()) {
                    String enc = new String(secretData.get(key), StandardCharsets.UTF_8);
                    writeData.put(key, enc);
                }
            }

            return writeData;
        }
        return new HashMap<>();
    }

    public void deleteSecret(String secretName) throws ApiException {
        try {
            api.readNamespacedSecret(secretName, namespace, "");
        } catch (ApiException e) {
            return;
        }

        //delete
        api.deleteNamespacedSecret(secretName, namespace, null, null, 0, null, null, "Foreground", null);
    }

    public void deleteSecretKeys(String secretName, Set<String> keys) throws JsonProcessingException, ApiException {
        V1Secret secret;
        try {
            secret = api.readNamespacedSecret(secretName, namespace, "");
        } catch (ApiException e) {
            return;
        }

        if (secret != null) {
            log.debug("delete {} from secret {}", keys, secretName);

            Map<String, byte[]> secretData = secret.getData();
            if (keys == null || secretData == null) {
                return;
            }

            //remove values from map and keep everything else
            keys.forEach(secretData::remove);

            if (secretData.isEmpty()) {
                //remove secret
                api.deleteNamespacedSecret(secretName, namespace, null, null, 0, null, null, "Foreground", null);
            } else {
                log.debug("patch existing secret {}", secretName);

                //write as base64 for patch
                Map<String, String> writeData = secretData
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Entry::getKey, d -> Base64.getEncoder().encodeToString(d.getValue())));

                PatchBody patchData = new PatchBody(writeData, "/data");
                V1Patch patch = new V1Patch(mapper.writeValueAsString(Collections.singleton(patchData)));

                //direct api call is broken as per 21.0.0 due to missing patch format
                // api.patchNamespacedSecret(secretName, namespace, patch, null, null, null, null, null);

                // json-patch via patch to avoid library bug
                PatchUtils.patch(
                    V1Secret.class,
                    () ->
                        api.patchNamespacedSecretCall(secretName, namespace, patch, null, null, null, null, null, null),
                    V1Patch.PATCH_FORMAT_JSON_PATCH,
                    api.getApiClient()
                );

                if (log.isTraceEnabled()) {
                    log.trace("patched secret {}", secretName);
                }
            }
        }
    }

    public void storeSecretData(@NotNull String secretName, Map<String, String> data)
        throws JsonProcessingException, ApiException {
        log.debug("store secret data for {}", secretName);

        V1Secret secret;
        try {
            secret = api.readNamespacedSecret(secretName, namespace, "");
        } catch (ApiException e) {
            // secret does not exist, create
            secret = null;
        }
        if (secret == null) {
            log.debug("create new secret {}", secretName);

            V1Secret body = new V1Secret()
                .metadata(new V1ObjectMeta().name(secretName).namespace(namespace))
                .apiVersion("v1")
                .kind("Secret")
                .stringData(data);
            api.createNamespacedSecret(namespace, body, null, null, null, null);

            if (log.isTraceEnabled()) {
                log.trace("created secret {}", secretName);
            }
        } else {
            log.debug("patch existing secret {}", secretName);

            //merge existing with new values
            Map<String, byte[]> secretData = MapUtils.mergeMultipleMaps(
                secret.getData(),
                data
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Entry::getKey, d -> d.getValue().getBytes(StandardCharsets.UTF_8)))
            );

            //write as base64 for patch
            Map<String, String> writeData = secretData
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Entry::getKey, d -> Base64.getEncoder().encodeToString(d.getValue())));

            PatchBody patchData = new PatchBody(writeData, "/data");
            V1Patch patch = new V1Patch(mapper.writeValueAsString(Collections.singleton(patchData)));
            //direct api call is broken as per 21.0.0 due to missing patch format
            // api.patchNamespacedSecret(secretName, namespace, patch, null, null, null, null, null);

            // json-patch via patch to avoid library bug
            PatchUtils.patch(
                V1Secret.class,
                () -> api.patchNamespacedSecretCall(secretName, namespace, patch, null, null, null, null, null, null),
                V1Patch.PATCH_FORMAT_JSON_PATCH,
                api.getApiClient()
            );

            if (log.isTraceEnabled()) {
                log.trace("patched secret {}", secretName);
            }
        }
    }

    public @Nullable V1Secret convertSecrets(String name, Map<String, String> values) {
        if (values != null) {
            //map to secret as envs
            //NOTE: keys should be usable, only sanitization is applied
            Map<String, String> data = values
                .entrySet()
                .stream()
                .filter(e -> e.getKey() != null)
                .collect(Collectors.toMap(e -> e.getKey().replaceAll("[^a-zA-Z0-9._-]+", ""), Entry::getValue));

            return new V1Secret()
                .metadata(new V1ObjectMeta().name(name).namespace(namespace))
                .apiVersion("v1")
                .kind("Secret")
                .stringData(data);
        }

        return null;
    }

    @Deprecated
    public @Nullable V1Secret convertAuthentication(String name, AbstractAuthenticationToken auth) {
        if (auth instanceof JwtAuthenticationToken) {
            Jwt token = ((JwtAuthenticationToken) auth).getToken();
            if (token == null) {
                throw new IllegalArgumentException("missing token");
            }

            String jwt = token.getTokenValue();
            String sub = token.getSubject();
            String username = token.getClaimAsString("preferred_username");

            Map<String, String> data = new HashMap<>();
            data.put("DIGITALHUB_CORE_TOKEN", jwt);
            data.put("DIGITALHUB_CORE_AUTH_SUB", sub);
            data.put("DIGITALHUB_CORE_USER", StringUtils.hasText(username) ? username : sub);

            return new V1Secret()
                .metadata(new V1ObjectMeta().name(name).namespace(namespace))
                .apiVersion("v1")
                .kind("Secret")
                .stringData(data);
        }

        return null;
    }

    // Generate and return job name
    public String getSecretName(String runtime, String task, String id) {
        return K8sBuilderHelper.sanitizeNames("sec" + "-" + task + "-" + id);
    }

    public record PatchBody(Object value, String path) {
        private static final String REPLACE_OPERATION = "replace";

        public String getOp() {
            return REPLACE_OPERATION;
        }
    }
}
