package it.smartcommunitylabdhub.runtime.ollama.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.smartcommunitylabdhub.runtime.ollama.config.OllamaProviderProperties;
import it.smartcommunitylabdhub.runtime.ollama.models.ModelDetail;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
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
public class OllamaRepositoryClient {

    public static final long CACHE_TIMEOUT = 3600; //seconds
    public static final String API_URL = "https://ollama.kinduff.com";
    public static final String REGISTRY_URL = "https://registry.ollama.ai";

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final OllamaProviderProperties properties;

    private final LoadingCache<String, Optional<ModelDetail>> metadataCache = CacheBuilder.newBuilder()
        .expireAfterWrite(CACHE_TIMEOUT, TimeUnit.SECONDS)
        .build(
            new CacheLoader<String, Optional<ModelDetail>>() {
                @Override
                public Optional<ModelDetail> load(@Nonnull String key) {
                    return Optional.ofNullable(fetchModelDetail(key));
                }
            }
        );

    private final LoadingCache<String, Optional<String>> readmeCache = CacheBuilder.newBuilder()
        .expireAfterWrite(CACHE_TIMEOUT, TimeUnit.SECONDS)
        .build(
            new CacheLoader<String, Optional<String>>() {
                @Override
                public Optional<String> load(@Nonnull String key) {
                    return Optional.ofNullable(fetchReadme(key));
                }
            }
        );

    public OllamaRepositoryClient(OllamaProviderProperties properties) {
        Assert.notNull(properties, "properties are required");
        this.properties = properties;
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

    /** Returns cached model metadata (all tags), or null when it cannot be fetched. */
    public ModelDetail getModelDetail(String key) {
        return metadataCache.getUnchecked(modelName(key)).orElse(null);
    }

    /** Returns sanitized README HTML, or null when unavailable. */
    public String getReadme(String key) {
        return readmeCache.getUnchecked(modelName(key)).orElse(null);
    }

    private ModelDetail fetchModelDetail(String key) {
        String url = apiUrl + "/api/models/" + key;
        log.debug("Getting metadata for model: {} from URL: {}", key, url);

        try {
            ResponseEntity<ModelDetail> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                ModelDetail.class
            );
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
            log.debug("Error getting metadata for model {}: {}", key, response.getStatusCode());
        } catch (RestClientException e) {
            log.debug("Error getting metadata for model {}: {}", key, e.getMessage());
        }

        return null;
    }

    private String fetchReadme(String key) {
        String path = key.contains("/") ? key : "library/" + key;
        String url = REGISTRY_URL + "/" + path;
        log.debug("Getting README for model: {} from URL: {}", key, url);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
            );
            if (response.getStatusCode() != HttpStatus.OK) {
                log.debug("Error getting README for model {}: {}", key, response.getStatusCode());
                return null;
            }

            String body = response.getBody();
            if (body != null) {
                Document document = Jsoup.parse(body, url);
                Element readme = document.selectFirst("#readme > div > div#display");
                if (readme != null) {
                    return Jsoup.clean(readme.html(), url, Safelist.relaxed());
                }
            }
            log.warn("README section missing from registry page for model {}", key);
        } catch (RestClientException e) {
            log.debug("Error getting README for model {}: {}", key, e.getMessage());
        }

        return null;
    }

    private String modelName(String key) {
        Assert.hasText(key, "model name is required");
        Assert.isTrue(
            key.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*(/[a-zA-Z0-9][a-zA-Z0-9._-]*)?(:[a-zA-Z0-9][a-zA-Z0-9._-]*)?"),
            "model name must be model or namespace/model, optionally followed by :tag"
        );

        String name = key.split(":", 2)[0];
        return name.startsWith("library/") ? name.substring("library/".length()) : name;
    }
}
