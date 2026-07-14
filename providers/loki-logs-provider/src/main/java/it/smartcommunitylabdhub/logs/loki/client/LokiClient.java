package it.smartcommunitylabdhub.logs.loki.client;

import it.smartcommunitylabdhub.logs.loki.config.LokiProperties;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Slf4j
public class LokiClient {

    private static final long NS_CONVERSION_FACTOR = 1_000_000L; // Convert milliseconds to nanoseconds
    private static final long S_CONVERSION_FACTOR = 1_000_000_000L; // Convert seconds to nanoseconds

    public static final int DEFAULT_LIMIT = 1000;
    public static final int DEFAULT_START_OFFSET = 8 * 60 * 60; //8 hours
    public static final String DIRECTION = "forward"; //default direction for queries

    private final String lokiUrl;
    private final String orgId;

    //basic auth support
    private final String username;
    private final String password;

    private final RestTemplate restTemplate;

    private Integer limit = DEFAULT_LIMIT; //default limit for queries

    public LokiClient(LokiProperties lokiProperties) {
        Assert.notNull(lokiProperties, "properties are required");
        Assert.hasText(lokiProperties.getUrl(), "loki url is required");

        this.lokiUrl = lokiProperties.getUrl();
        this.orgId = lokiProperties.getOrgId();
        this.username = lokiProperties.getUsername();
        this.password = lokiProperties.getPassword();

        restTemplate = new RestTemplate();

        List<HttpMessageConverter<?>> converters = List.of(
            new StringHttpMessageConverter(),
            new MappingJackson2HttpMessageConverter()
        );
        restTemplate.setMessageConverters(converters);
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public QueryResult query(
        @NotNull String query,
        @Nullable Long start,
        @Nullable Long end,
        @Nullable String direction
    ) {
        log.debug("query loki for {} interval {} - {}", query, String.valueOf(start), String.valueOf(end));
        // parameters are unix seconds; convert to nanoseconds for Loki
        long nowNs = System.currentTimeMillis() * NS_CONVERSION_FACTOR;
        long endNs = end != null ? end * S_CONVERSION_FACTOR : nowNs;
        long startNs = start != null ? start * S_CONVERSION_FACTOR : endNs - DEFAULT_START_OFFSET * S_CONVERSION_FACTOR;
        String dir = direction != null ? direction : DIRECTION;

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(lokiUrl + "/loki/api/v1/query_range")
            .queryParam("query", UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8))
            .queryParam("start", startNs)
            .queryParam("end", endNs)
            .queryParam("limit", limit)
            .queryParam("direction", dir);

        HttpHeaders headers = new HttpHeaders();
        if (orgId != null && !orgId.isBlank()) {
            headers.set("X-Scope-OrgID", orgId);
        }
        if (authRequired()) {
            headers.set(HttpHeaders.AUTHORIZATION, getAuthHeader());
        }

        URI uri = uriBuilder.build(true).toUri();
        if (log.isTraceEnabled()) {
            log.trace("querying loki: {}", uri);
        }

        try {
            ResponseEntity<QueryResult> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                QueryResult.class
            );

            return response.getBody();
        } catch (ResourceAccessException e) {
            // Connection refused, timeout, or other I/O errors
            log.error("Error connecting to loki at {}: {}", lokiUrl, e.getMessage());
            throw new LokiException("Cannot connect to Loki at " + lokiUrl + ": " + e.getMessage(), e);
        } catch (HttpClientErrorException e) {
            // 4xx errors (e.g. 401, 403, 404)
            log.error("`Error response from loki at {}: {} - {}", lokiUrl, e.getStatusCode().value(), e.getMessage());
            throw new LokiException(
                "Loki request failed with client error " + e.getStatusCode().value() + ": " + e.getMessage(),
                e.getStatusCode().value(),
                e
            );
        } catch (HttpServerErrorException e) {
            // 5xx errors
            log.error("Error response from loki at {}: {} - {}", lokiUrl, e.getStatusCode().value(), e.getMessage());
            throw new LokiException(
                "Loki request failed with server error " + e.getStatusCode().value() + ": " + e.getMessage(),
                e.getStatusCode().value(),
                e
            );
        } catch (RestClientException e) {
            log.error("Error during loki request to {}: {}", lokiUrl, e.getMessage());
            throw new LokiException("Loki request failed: " + e.getMessage(), e);
        }
    }

    private boolean authRequired() {
        return username != null && password != null;
    }

    private String getAuthHeader() {
        if (authRequired()) {
            String credentials = username + ":" + password;
            return "Basic " + java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        }
        return null;
    }
}
