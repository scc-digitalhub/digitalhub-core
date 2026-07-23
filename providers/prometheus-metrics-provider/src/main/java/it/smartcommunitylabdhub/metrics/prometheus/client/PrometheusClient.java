package it.smartcommunitylabdhub.metrics.prometheus.client;

import it.smartcommunitylabdhub.metrics.config.PrometheusProperties;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
public class PrometheusClient {

    public static final int DEFAULT_LIMIT = 100;
    public static final int DEFAULT_START_OFFSET = 8 * 60 * 60; //8 hours
    public static final Duration DEFAULT_STEP = Duration.ofSeconds(15); //default step for queries

    private final String prometheusUrl;

    //basic auth support
    private final String username;
    private final String password;

    private final RestTemplate restTemplate;

    private Integer limit = DEFAULT_LIMIT; //default limit for queries

    public PrometheusClient(PrometheusProperties properties) {
        Assert.notNull(properties, "properties are required");
        Assert.hasText(properties.getUrl(), "prometheus url is required");

        this.prometheusUrl = properties.getUrl();
        this.username = properties.getUsername();
        this.password = properties.getPassword();

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

    public QueryResult queryRange(
        @NotNull String query,
        @Nullable Long start,
        @Nullable Long end,
        @Nullable Duration step
    ) {
        log.debug("query prometheus for {} interval {} - {}", query, String.valueOf(start), String.valueOf(end));
        // parameters are unix seconds;
        long nowSeconds = Instant.now().getEpochSecond();

        long endTs = end != null ? end : nowSeconds;
        long startTs = start != null ? start : endTs - DEFAULT_START_OFFSET;

        Duration queryStep = step != null ? step : DEFAULT_STEP;

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(prometheusUrl + "/api/v1/query_range")
            .queryParam("query", UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8))
            .queryParam("start", startTs)
            .queryParam("end", endTs)
            .queryParam("limit", limit)
            .queryParam("step", queryStep.toSeconds());

        HttpHeaders headers = new HttpHeaders();
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, getAuthHeader());
        }

        URI uri = uriBuilder.build(true).toUri();
        if (log.isTraceEnabled()) {
            log.trace("querying prometheus: {}", uri);
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
            log.error("Error connecting to prometheus at {}: {}", prometheusUrl, e.getMessage());
            throw new PrometheusException(
                "Cannot connect to Prometheus at " + prometheusUrl + ": " + e.getMessage(),
                e
            );
        } catch (HttpClientErrorException e) {
            // 4xx errors (e.g. 401, 403, 404)
            log.error(
                "`Error response from prometheus at {}: {} - {}",
                prometheusUrl,
                e.getStatusCode().value(),
                e.getMessage()
            );
            throw new PrometheusException(
                "Prometheus request failed with client error " + e.getStatusCode().value() + ": " + e.getMessage(),
                e.getStatusCode().value(),
                e
            );
        } catch (HttpServerErrorException e) {
            // 5xx errors
            log.error(
                "Error response from prometheus at {}: {} - {}",
                prometheusUrl,
                e.getStatusCode().value(),
                e.getMessage()
            );
            throw new PrometheusException(
                "Prometheus request failed with server error " + e.getStatusCode().value() + ": " + e.getMessage(),
                e.getStatusCode().value(),
                e
            );
        } catch (RestClientException e) {
            log.error("Error during prometheus request to {}: {}", prometheusUrl, e.getMessage());
            throw new PrometheusException("Prometheus request failed: " + e.getMessage(), e);
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
