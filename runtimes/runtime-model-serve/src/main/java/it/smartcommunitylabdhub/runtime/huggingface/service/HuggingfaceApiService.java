package it.smartcommunitylabdhub.runtime.huggingface.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.smartcommunitylabdhub.runtime.huggingface.config.HuggingfaceProperties;
import jakarta.annotation.Nonnull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@Validated
public class HuggingfaceApiService {

    public static final long CACHE_TIMEOUT = 3600; //seconds
    public static final String API_URL = "https://huggingface.co";
    private static final ParameterizedTypeReference<HashMap<String, Serializable>> paramRef =
        new ParameterizedTypeReference<HashMap<String, Serializable>>() {};
    public static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<>() {};

    private final HuggingfaceProperties properties;
    private final String apiUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    LoadingCache<String, Optional<Map<String, Serializable>>> metadataCache = CacheBuilder.newBuilder()
        .expireAfterWrite(CACHE_TIMEOUT, TimeUnit.SECONDS)
        .build(
            new CacheLoader<String, Optional<Map<String, Serializable>>>() {
                @Override
                public Optional<Map<String, Serializable>> load(@Nonnull String key) throws Exception {
                    return Optional.ofNullable(fetchMetadata(key));
                }
            }
        );

    LoadingCache<String, Optional<Map<String, Serializable>>> configCache = CacheBuilder.newBuilder()
        .expireAfterWrite(CACHE_TIMEOUT, TimeUnit.SECONDS)
        .build(
            new CacheLoader<String, Optional<Map<String, Serializable>>>() {
                @Override
                public Optional<Map<String, Serializable>> load(@Nonnull String key) throws Exception {
                    return Optional.ofNullable(fetchConfig(key));
                }
            }
        );
    LoadingCache<String, Optional<String>> readmeCache = CacheBuilder.newBuilder()
        .expireAfterWrite(CACHE_TIMEOUT, TimeUnit.SECONDS)
        .build(
            new CacheLoader<String, Optional<String>>() {
                @Override
                public Optional<String> load(@Nonnull String key) throws Exception {
                    return Optional.ofNullable(fetchReadme(key));
                }
            }
        );

    public HuggingfaceApiService(HuggingfaceProperties huggingfaceProperties) {
        Assert.notNull(huggingfaceProperties, "properties are required");
        this.properties = huggingfaceProperties;
        this.apiUrl = StringUtils.hasText(properties.getApiUrl()) ? properties.getApiUrl() : API_URL;

        //build RestTemplate with custom message converters
        restTemplate = new RestTemplate();

        List<HttpMessageConverter<?>> converters = List.of(
            new StringHttpMessageConverter(),
            new MappingJackson2HttpMessageConverter(),
            new FormHttpMessageConverter()
        );
        restTemplate.setMessageConverters(converters);
    }

    public Map<String, Serializable> getMetadata(String key) {
        Optional<Map<String, Serializable>> entry = metadataCache.getUnchecked(key);
        return entry.orElse(null);
    }

    private Map<String, Serializable> fetchMetadata(String key) {
        // Implement the logic to fetch metadata from the Huggingface API
        String manifestUrl = apiUrl + "/api/models/" + key;
        StringBuilder params = new StringBuilder("?");
        Optional.ofNullable(properties.getMetadata()).ifPresent(metadata -> {
            for (String m : metadata) {
                if (params.length() > 1) {
                    params.append("&");
                }
                params.append("expand[]").append("=").append(m);
            }
        });

        log.debug("Getting metadata for model: {} from URL: {}", key, manifestUrl);

        try {
            ResponseEntity<HashMap<String, Serializable>> response = restTemplate.exchange(
                manifestUrl + params.toString(),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                paramRef
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                log.debug("error getting config for model {}: {}", key, response.getStatusCode());
            }

            return response.getBody();
        } catch (RestClientException e) {
            log.debug("error getting config for model {}: {}", key, e.getMessage());
        }

        return null;
    }

    public Map<String, Serializable> getConfig(String key) {
        Optional<Map<String, Serializable>> entry = configCache.getUnchecked(key);
        return entry.orElse(null);
    }

    private Map<String, Serializable> fetchConfig(String key) {
        // Implement the logic to fetch config from the Huggingface API
        String configUrl = apiUrl + "/" + key + "/resolve/main/config.json";

        log.debug("Getting config for model: {} from URL: {}", key, configUrl);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                configUrl,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                log.debug("error getting config for model {}: {}", key, response.getStatusCode());
            }

            String value = response.getBody();
            if (value != null && !value.isEmpty()) {
                try {
                    return objectMapper.readValue(value, typeRef);
                } catch (JsonProcessingException e) {
                    log.debug("error parsing config for model {}: {}", key, e.getMessage());
                }
            }
        } catch (RestClientException e) {
            log.debug("error getting config for model {}: {}", key, e.getMessage());
        }

        //empty response body
        return null;
    }

    public String getReadme(String key) {
        Optional<String> entry = readmeCache.getUnchecked(key);
        return entry.orElse(null);
    }

    private String fetchReadme(String key) {
        // Implement the logic to fetch README from the Huggingface API
        String readmeUrl = apiUrl + "/" + key + "/resolve/main/README.md";

        log.debug("Getting README for model: {} from URL: {}", key, readmeUrl);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                readmeUrl,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                log.debug("error getting README for model {}: {}", key, response.getStatusCode());
            }

            return response.getBody();
        } catch (RestClientException e) {
            log.debug("error getting config for model {}: {}", key, e.getMessage());
        }

        return null;
    }
}
