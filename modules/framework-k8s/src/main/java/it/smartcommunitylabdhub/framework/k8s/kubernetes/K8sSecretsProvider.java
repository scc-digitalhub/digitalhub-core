package it.smartcommunitylabdhub.framework.k8s.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kubernetes.client.openapi.ApiException;
import it.smartcommunitylabdhub.commons.annotations.common.Identifier;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.services.SecretsProvider;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Identifier(K8sSecretsProvider.K8S_PROVIDER)
@Component
@Slf4j
@ConditionalOnKubernetes
public class K8sSecretsProvider implements SecretsProvider {

    public static final String K8S_PROVIDER = "kubernetes";
    private static final String PATH_FORMAT = "%s://%s/%s";
    private static final Pattern PATH_PATTERN = Pattern.compile("(\\w+)://([\\w-]+)/([\\w-]+)");

    private final K8sSecretHelper secretHelper;

    public K8sSecretsProvider(K8sSecretHelper secretHelper) {
        Assert.notNull(secretHelper, "secrets helper is required");
        this.secretHelper = secretHelper;
    }

    @Override
    public String readSecretData(@NotNull String path) throws StoreException {
        log.debug("read secrets data from {}", String.valueOf(path));

        Matcher matcher = PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid path");
        }

        //read keys
        String name = matcher.group(1);
        String key = matcher.group(2);

        //unseal
        try {
            Map<String, String> data = secretHelper.getSecretData(name);
            //value may be null
            return data.get(key);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new StoreException("error with k8s");
        }
    }

    @Override
    public void writeSecretData(@NotNull String path, @NotNull String value) throws StoreException {
        log.debug("write secrets data for {}", String.valueOf(path));

        Matcher matcher = PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid path");
        }

        //read keys
        String name = matcher.group(1);
        String key = matcher.group(2);

        //unseal
        try {
            //write, will create or append
            secretHelper.storeSecretData(name, Collections.singletonMap(key, value));
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new StoreException("error with k8s");
        } catch (JsonProcessingException e) {
            log.error("Error writing secrets: {}", e.getMessage());

            throw new StoreException("error writing secret");
        }
    }

    @Override
    public void clearSecretData(@NotNull String path) throws StoreException {
        log.debug("clear secrets data for {}", String.valueOf(path));

        Matcher matcher = PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid path");
        }

        //read keys
        String name = matcher.group(1);
        String key = matcher.group(2);

        //unseal
        try {
            //remove key, when empty removes secret as well
            secretHelper.deleteSecretKeys(name, Collections.singleton(key));
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new StoreException("error with k8s");
        } catch (JsonProcessingException e) {
            log.error("Error writing secrets: {}", e.getMessage());

            throw new StoreException("error writing secret");
        }
    }
}
