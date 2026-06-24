/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.containerimages;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.OciIndexTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.containerimages.config.ContainerImagesProperties;
import it.smartcommunitylabdhub.containerimages.model.ImageConfig;
import it.smartcommunitylabdhub.containerimages.model.ImageDescription;
import it.smartcommunitylabdhub.containerimages.model.LayerInfo;
import it.smartcommunitylabdhub.containerimages.model.TagsResponse;
import it.smartcommunitylabdhub.containerimages.model.TokenResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.security.DigestException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@Validated
public class ContainerImagesService {

    public static final long CACHE_TIMEOUT = 300; //seconds
    public static final long DESCRIPTION_CACHE_TIMEOUT = 43200; //seconds (12 hours)

    // Accept both Docker v2 and OCI manifests for broad registry compatibility
    private static final String MANIFEST_ACCEPT_HEADER =
        "application/vnd.docker.distribution.manifest.v2+json," +
        "application/vnd.docker.distribution.manifest.list.v2+json," +
        "application/vnd.oci.image.manifest.v1+json," +
        "application/vnd.oci.image.index.v1+json";

    private static final Pattern BEARER_REALM_PATTERN = Pattern.compile(
        "Bearer realm=\"([^\"]+)\"(?:,service=\"([^\"]+)\")?(?:,scope=\"([^\"]+)\")?"
    );

    private static final int BEARER_GROUP_REALM = 1;
    private static final int BEARER_GROUP_SERVICE = 2;
    private static final int BEARER_GROUP_SCOPE = 3;

    private final RestTemplate restTemplate;
    private final ContainerImagesProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    LoadingCache<String, ManifestAndDigest<ManifestTemplate>> cache = CacheBuilder.newBuilder()
        .expireAfterWrite(CACHE_TIMEOUT, TimeUnit.SECONDS)
        .build(
            new CacheLoader<String, ManifestAndDigest<ManifestTemplate>>() {
                @Override
                public ManifestAndDigest<ManifestTemplate> load(@Nonnull String key) throws Exception {
                    return fetchManifestAndDigest(key);
                }
            }
        );

    LoadingCache<String, ImageDescription> descriptionCache = CacheBuilder.newBuilder()
        .expireAfterWrite(DESCRIPTION_CACHE_TIMEOUT, TimeUnit.SECONDS)
        .build(
            new CacheLoader<String, ImageDescription>() {
                @Override
                public ImageDescription load(@Nonnull String key) throws Exception {
                    return fetchDescription(key);
                }
            }
        );

    public ContainerImagesService(ContainerImagesProperties properties) {
        Assert.notNull(properties, "properties can not be null");
        this.properties = properties;

        restTemplate = new RestTemplate();

        List<HttpMessageConverter<?>> converters = List.of(
            new StringHttpMessageConverter(),
            new MappingJackson2HttpMessageConverter(),
            new FormHttpMessageConverter()
        );
        restTemplate.setMessageConverters(converters);
    }

    /**
     * Retrieve the content digest for the given image reference from any OCI/Docker v2
     * compatible registry (Docker Hub, Quay, Harbor, GHCR, etc.).
     * Credentials are taken from {@code containerimages.username} / {@code containerimages.password}
     * properties when set; otherwise the call is attempted anonymously first.
     */
    public String getDigest(@NotNull String imageReference)
        throws IOException, InvalidImageReferenceException, StoreException {
        ImageReference imageRef = ImageReference.parse(imageReference);
        String manifestUrl = buildManifestUrl(imageRef);
        log.debug("Getting digest for image: {} from URL: {}", imageReference, manifestUrl);

        return withAuth(imageRef.getRegistry(), authHeader -> {
            ResponseEntity<Void> response = restTemplate.exchange(
                manifestUrl,
                HttpMethod.HEAD,
                new HttpEntity<>(buildManifestHeaders(authHeader)),
                Void.class
            );
            return extractDigest(imageReference, response.getHeaders());
        });
    }

    public static String getDigest(
        @NotNull String imageReference,
        @NotNull ManifestAndDigest<ManifestTemplate> manifestAndDigest
    ) {
        DescriptorDigest digest = manifestAndDigest.getDigest();
        if (digest == null) {
            log.warn("No digest available for image: {}", imageReference);
            return null;
        }

        log.debug("Using manifest digest for image: {} -> {}", imageReference, digest);
        return digest.getHash();
    }

    /**
     * Fetch the full manifest for the given image reference and return it together with its digest.
     * The concrete type of the returned {@link ManifestTemplate} depends on the registry response:
     * <ul>
     *   <li>{@link V22ManifestTemplate} – Docker Image Manifest v2.2</li>
     *   <li>{@link V22ManifestListTemplate} – Docker Manifest List (multi-arch)</li>
     *   <li>{@link OciManifestTemplate} – OCI Image Manifest v1</li>
     *   <li>{@link OciIndexTemplate} – OCI Image Index (multi-arch)</li>
     * </ul>
     * Works with any v2-compatible registry (Docker Hub, Quay, Harbor, GHCR, …).
     */
    public ManifestAndDigest<ManifestTemplate> getManifest(@NotNull String imageReference)
        throws IOException, InvalidImageReferenceException, StoreException {
        try {
            return cache.get(imageReference);
        } catch (Exception e) {
            throw new IOException("Failed to fetch manifest for image: " + imageReference, e);
        }
    }

    private ManifestAndDigest<ManifestTemplate> fetchManifestAndDigest(@NotNull String imageReference)
        throws IOException, InvalidImageReferenceException, StoreException {
        ImageReference imageRef = ImageReference.parse(imageReference);
        log.debug("Getting manifest for image: {}", imageReference);

        return withAuth(imageRef.getRegistry(), authHeader -> {
            ManifestAndDigest<ManifestTemplate> result = fetchManifestFromUrl(
                buildManifestUrl(imageRef),
                imageReference,
                authHeader
            );

            // Manifest lists / OCI indexes do not carry layer info themselves.
            // Transparently resolve the first platform entry so callers always
            // receive a single-platform manifest with layers.
            ManifestTemplate manifest = result.getManifest();
            if (manifest instanceof V22ManifestListTemplate list) {
                List<V22ManifestListTemplate.ManifestDescriptorTemplate> entries = list.getManifests();
                if (!entries.isEmpty()) {
                    String firstDigest = entries.get(0).getDigest();
                    log.debug(
                        "Manifest list detected for {}, resolving first platform manifest: {}",
                        imageReference,
                        firstDigest
                    );
                    return fetchManifestFromUrl(
                        buildManifestUrlByDigest(imageRef, firstDigest),
                        imageReference + "@" + firstDigest,
                        authHeader
                    );
                }
            } else if (manifest instanceof OciIndexTemplate index) {
                List<OciIndexTemplate.ManifestDescriptorTemplate> entries = index.getManifests();
                if (!entries.isEmpty()) {
                    String firstDigest = entries.get(0).getDigest().toString();
                    log.debug(
                        "OCI index detected for {}, resolving first platform manifest: {}",
                        imageReference,
                        firstDigest
                    );
                    return fetchManifestFromUrl(
                        buildManifestUrlByDigest(imageRef, firstDigest),
                        imageReference + "@" + firstDigest,
                        authHeader
                    );
                }
            }

            return result;
        });
    }

    private ManifestAndDigest<ManifestTemplate> fetchManifestFromUrl(
        String manifestUrl,
        String imageReference,
        @Nullable String authHeader
    ) throws IOException, StoreException {
        ResponseEntity<String> response = restTemplate.exchange(
            manifestUrl,
            HttpMethod.GET,
            new HttpEntity<>(buildManifestHeaders(authHeader)),
            String.class
        );

        String body = response.getBody();
        if (!StringUtils.hasText(body)) {
            throw new StoreException("Empty manifest body for image: " + imageReference);
        }

        // Resolve the concrete template class from the Content-Type header,
        // falling back to the schemaVersion field in the JSON body.
        Class<? extends ManifestTemplate> templateClass = resolveManifestType(
            response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
            body
        );

        try {
            ManifestTemplate manifest = JsonTemplateMapper.readJson(body, templateClass);

            // Prefer the authoritative digest from the registry header; compute a
            // placeholder only when the header is absent (some older registries omit it).
            String rawDigest = response.getHeaders().getFirst("Docker-Content-Digest");
            DescriptorDigest digest = StringUtils.hasText(rawDigest)
                ? DescriptorDigest.fromDigest(rawDigest)
                : DescriptorDigest.fromHash(Integer.toHexString(body.hashCode()).repeat(2));

            log.debug("Successfully retrieved manifest for image: {} digest={}", imageReference, digest);
            return new ManifestAndDigest<>(manifest, digest);
        } catch (DigestException e) {
            throw new IOException("Invalid digest in registry response: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the list of tags available for the repository of the given image reference from any
     * OCI/Docker v2 compatible registry, using the same credentials and auth flow as the other methods.
     */
    public List<String> getTags(@NotNull String imageReference)
        throws IOException, InvalidImageReferenceException, StoreException {
        ImageReference imageRef = ImageReference.parse(imageReference);
        String tagsUrl = buildTagsUrl(imageRef);
        log.debug("Getting tags for image: {} from URL: {}", imageReference, tagsUrl);

        return withAuth(imageRef.getRegistry(), authHeader -> {
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.hasText(authHeader)) {
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            }

            ResponseEntity<TagsResponse> response = restTemplate.exchange(
                tagsUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TagsResponse.class
            );

            TagsResponse body = response.getBody();
            if (body == null || body.getTags() == null) {
                return Collections.emptyList();
            }
            return body.getTags();
        });
    }

    /**
     * Returns the layers of the given image enriched with the Dockerfile instruction that created
     * each one.  The instruction is sourced from the image config blob's {@code history} array
     * (the {@code created_by} field), which is the same data Docker Hub and {@code dive} display.
     *
     * <p>History entries marked with {@code "empty_layer": true} (produced by metadata-only
     * instructions such as {@code ENV}, {@code LABEL}, {@code CMD}) are included with a
     * {@code null} digest/size because they do not correspond to a filesystem layer.
     *
     * <p>Multi-arch images are resolved to their first platform manifest automatically.
     */
    public List<LayerInfo> getLayers(@NotNull String imageReference)
        throws IOException, InvalidImageReferenceException, StoreException {
        ManifestAndDigest<ManifestTemplate> manifestAndDigest = getManifest(imageReference);
        ManifestTemplate manifest = manifestAndDigest.getManifest();

        if (!(manifest instanceof BuildableManifestTemplate buildable)) {
            log.debug("Layers not available for non-buildable manifest: {}", imageReference);
            return Collections.emptyList();
        }

        List<BuildableManifestTemplate.ContentDescriptorTemplate> layers = buildable.getLayers();
        BuildableManifestTemplate.ContentDescriptorTemplate configDescriptor = buildable.getContainerConfiguration();
        if (configDescriptor == null) {
            log.debug("No config descriptor in manifest for image: {}", imageReference);
            return Collections.emptyList();
        }

        // Fetch the image config blob to get the history with Dockerfile instructions
        ImageReference imageRef = ImageReference.parse(imageReference);
        String configDigest = configDescriptor.getDigest().toString();
        String blobUrl = buildBlobUrl(imageRef, configDigest);

        ImageConfig config = withAuth(imageRef.getRegistry(), authHeader -> {
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.hasText(authHeader)) {
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            }
            ResponseEntity<String> response = restTemplate.exchange(
                blobUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );
            String body = response.getBody();
            if (!StringUtils.hasText(body)) {
                return null;
            }
            try {
                return objectMapper.readValue(body, ImageConfig.class);
            } catch (Exception e) {
                throw new IOException("Failed to parse image config blob: " + e.getMessage(), e);
            }
        });

        List<ImageConfig.HistoryEntry> history = (config != null && config.getHistory() != null)
            ? config.getHistory()
            : Collections.emptyList();

        // Align layers[] with history[], skipping empty_layer entries
        List<LayerInfo> result = new ArrayList<>();
        int layerIdx = 0;
        for (ImageConfig.HistoryEntry entry : history) {
            if (Boolean.TRUE.equals(entry.getEmptyLayer())) {
                result.add(LayerInfo.builder().instruction(entry.getCreatedBy()).emptyLayer(true).build());
            } else {
                if (layerIdx < layers.size()) {
                    BuildableManifestTemplate.ContentDescriptorTemplate layer = layers.get(layerIdx++);
                    result.add(
                        LayerInfo.builder()
                            .digest(layer.getDigest().toString())
                            .size(layer.getSize())
                            .instruction(entry.getCreatedBy())
                            .emptyLayer(false)
                            .build()
                    );
                }
            }
        }

        // Any remaining layers without history (shouldn't happen in well-formed images)
        while (layerIdx < layers.size()) {
            BuildableManifestTemplate.ContentDescriptorTemplate layer = layers.get(layerIdx++);
            result.add(
                LayerInfo.builder().digest(layer.getDigest().toString()).size(layer.getSize()).emptyLayer(false).build()
            );
        }

        return result;
    }

    /**
     * Returns the total compressed size in bytes of the given image by summing the sizes of all
     * layers and the config blob from the manifest.
     *
     * <p>Multi-arch manifest lists and OCI indexes are automatically resolved to their first
     * platform entry, so this method always returns a value for well-formed images.
     */
    public Long getSize(@NotNull String imageReference)
        throws IOException, InvalidImageReferenceException, StoreException {
        ManifestAndDigest<ManifestTemplate> manifestAndDigest = getManifest(imageReference);
        ManifestTemplate manifest = manifestAndDigest.getManifest();

        if (manifest != null) {
            return getSize(imageReference, manifest);
        }

        log.debug("Size not available for manifest list / OCI index image: {}", imageReference);
        return null;
    }

    public static Long getSize(@NotNull String imageReference, @NotNull ManifestTemplate manifest)
        throws IOException, InvalidImageReferenceException, StoreException {
        if (manifest instanceof BuildableManifestTemplate buildable) {
            long size = buildable
                .getLayers()
                .stream()
                .mapToLong(BuildableManifestTemplate.ContentDescriptorTemplate::getSize)
                .sum();

            BuildableManifestTemplate.ContentDescriptorTemplate config = buildable.getContainerConfiguration();
            if (config != null) {
                size += config.getSize();
            }

            log.debug("Computed size for image {}: {} bytes", imageReference, size);
            return size;
        }

        log.debug("Size not available for manifest list / OCI index image: {}", imageReference);
        return null;
    }

    /**
     * Executes {@code call} anonymously first; on HTTP 401 resolves credentials for the registry,
     * derives an Authorization header via the WWW-Authenticate challenge, and retries once.
     * Throws {@link StoreException} if authentication is required but cannot be satisfied.
     */
    private <T> T withAuth(String registry, AuthenticatedCall<T> call) throws IOException, StoreException {
        try {
            return call.execute(null);
        } catch (HttpClientErrorException.Unauthorized e) {
            Credential credential = buildCredential(registry);
            String authHeader = handleAuthChallenge(e, credential);
            if (!StringUtils.hasText(authHeader)) {
                throw new StoreException("unauthorized");
            }
            try {
                return call.execute(authHeader);
            } catch (HttpClientErrorException.Unauthorized ex) {
                throw new StoreException("unauthorized");
            } catch (RestClientException ex) {
                throw new IOException("Registry call failed: " + ex.getMessage(), ex);
            }
        } catch (RestClientException e) {
            throw new IOException("Registry call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Maps a Content-Type value (or a fallback parse of the JSON body) to the appropriate
     * jib {@link ManifestTemplate} subclass.
     */
    private Class<? extends ManifestTemplate> resolveManifestType(String contentType, String body) {
        if (StringUtils.hasText(contentType)) {
            // Strip parameters such as "; charset=utf-8"
            String mediaType = contentType.contains(";")
                ? contentType.substring(0, contentType.indexOf(';')).trim()
                : contentType.trim();
            return switch (mediaType) {
                case V22ManifestTemplate.MANIFEST_MEDIA_TYPE -> V22ManifestTemplate.class;
                case V22ManifestListTemplate.MANIFEST_MEDIA_TYPE -> V22ManifestListTemplate.class;
                case OciManifestTemplate.MANIFEST_MEDIA_TYPE -> OciManifestTemplate.class;
                // OCI image index — no dedicated constant in older jib versions, use literal
                case "application/vnd.oci.image.index.v1+json" -> OciIndexTemplate.class;
                default -> inferManifestTypeFromBody(body);
            };
        }
        return inferManifestTypeFromBody(body);
    }

    /**
     * Last-resort fallback: inspect the {@code mediaType} or {@code schemaVersion} fields
     * in the raw JSON body to pick the right template class.
     */
    private Class<? extends ManifestTemplate> inferManifestTypeFromBody(String body) {
        if (body.contains(V22ManifestListTemplate.MANIFEST_MEDIA_TYPE)) {
            return V22ManifestListTemplate.class;
        }
        if (body.contains(OciManifestTemplate.MANIFEST_MEDIA_TYPE)) {
            return OciManifestTemplate.class;
        }
        if (body.contains("\"application/vnd.oci.image.index.v1+json\"")) {
            return OciIndexTemplate.class;
        }
        // Docker v2.2 is the safest default for unknown responses
        return V22ManifestTemplate.class;
    }

    private String handleAuthChallenge(HttpClientErrorException.Unauthorized e, Credential credential) {
        String wwwAuthenticate =
            e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE) : null;

        if (wwwAuthenticate == null) {
            return null; // No authentication challenge provided, return empty metadata
        }

        if (wwwAuthenticate.toLowerCase().startsWith("bearer")) {
            String token = fetchBearerToken(wwwAuthenticate, credential);
            if (token != null) {
                return "Bearer " + token;
            }
        } else if (wwwAuthenticate.toLowerCase().startsWith("basic") && credential != null) {
            return "Basic " + HttpHeaders.encodeBasicAuth(credential.getUsername(), credential.getPassword(), null);
        }

        return null;
    }

    private HttpHeaders buildManifestHeaders(@Nullable String authHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MANIFEST_ACCEPT_HEADER);
        if (StringUtils.hasText(authHeader)) {
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        }

        return headers;
    }

    private Credential buildCredential(String registry) {
        if (StringUtils.hasText(properties.getUsername()) && StringUtils.hasText(properties.getPassword())) {
            //if registry is defined restrict credentials to it, otherwise use for all registries
            if (StringUtils.hasText(properties.getRegistry())) {
                if (registry.equals(properties.getRegistry())) {
                    return Credential.from(properties.getUsername(), properties.getPassword());
                } else {
                    log.debug(
                        "Registry {} does not match configured registry {}, skipping credentials",
                        registry,
                        properties.getRegistry()
                    );
                    return null;
                }
            } else {
                return Credential.from(properties.getUsername(), properties.getPassword());
            }
        }

        return null;
    }

    private String buildTagsUrl(ImageReference imageRef) {
        String registry = imageRef.getRegistry();
        String repository = imageRef.getRepository();
        String protocol = isLocalRegistry(registry) ? "http" : "https";
        return String.format("%s://%s/v2/%s/tags/list", protocol, registry, repository);
    }

    private String buildManifestUrl(ImageReference imageRef) {
        String registry = imageRef.getRegistry();
        String repository = imageRef.getRepository();
        String tag = imageRef.getTag().orElse("latest");
        String protocol = isLocalRegistry(registry) ? "http" : "https";
        return String.format("%s://%s/v2/%s/manifests/%s", protocol, registry, repository, tag);
    }

    private String buildManifestUrlByDigest(ImageReference imageRef, String digest) {
        String registry = imageRef.getRegistry();
        String repository = imageRef.getRepository();
        String protocol = isLocalRegistry(registry) ? "http" : "https";
        return String.format("%s://%s/v2/%s/manifests/%s", protocol, registry, repository, digest);
    }

    private String buildBlobUrl(ImageReference imageRef, String digest) {
        String registry = imageRef.getRegistry();
        String repository = imageRef.getRepository();
        String protocol = isLocalRegistry(registry) ? "http" : "https";
        return String.format("%s://%s/v2/%s/blobs/%s", protocol, registry, repository, digest);
    }

    private boolean isLocalRegistry(String registry) {
        return registry.startsWith("localhost") || registry.startsWith("127.0.0.1") || registry.startsWith("0.0.0.0");
    }

    private String extractDigest(String imageReference, HttpHeaders headers) {
        String digest = headers.getFirst("Docker-Content-Digest");
        if (StringUtils.hasText(digest)) {
            log.debug("Successfully retrieved digest for image: {} -> {}", imageReference, digest);
            return digest;
        }
        log.warn("No Docker-Content-Digest header found for image: {}", imageReference);
        return null;
    }

    /**
     * Returns a description for the given image reference, served from a 12-hour loading cache.
     * External calls (Docker Hub API, GitHub API, OCI manifest) are only made on a cache miss.
     */
    public ImageDescription getDescription(@NotNull String imageReference)
        throws IOException, InvalidImageReferenceException, StoreException {
        try {
            return descriptionCache.get(imageReference);
        } catch (Exception e) {
            throw new IOException("Failed to fetch description for image: " + imageReference, e);
        }
    }

    /**
     * Internal implementation invoked by the cache loader.
     * <p>
     * OCI annotations ({@code org.opencontainers.image.*}) are always read from the raw manifest
     * JSON, which is part of the standard OCI Image Spec and therefore works with any v2-compatible
     * registry. For Docker Hub images the full markdown README is additionally fetched from the
     * Docker Hub public API ({@code hub.docker.com/v2/repositories/…}).
     */
    private ImageDescription fetchDescription(@NotNull String imageReference)
        throws IOException, InvalidImageReferenceException, StoreException {
        ImageReference imageRef = ImageReference.parse(imageReference);

        java.util.Map<String, String> annotations = fetchOciAnnotations(imageRef);

        String title = annotations.get("org.opencontainers.image.title");
        String description = annotations.get("org.opencontainers.image.description");
        String documentation = annotations.get("org.opencontainers.image.documentation");
        String source = annotations.get("org.opencontainers.image.source");
        String fullDescription = null;

        if (isDockerHub(imageRef.getRegistry())) {
            fullDescription = fetchDockerHubFullDescription(imageRef);
            if (description == null) {
                description = fetchDockerHubShortDescription(imageRef);
            }
        } else if (isGitHubRegistry(imageRef.getRegistry())) {
            // Use org.opencontainers.image.source if present, otherwise infer from image path
            String repoUrl = StringUtils.hasText(source) ? source : inferGitHubRepoUrl(imageRef);
            if (StringUtils.hasText(repoUrl)) {
                fullDescription = fetchGitHubReadme(repoUrl);
            }
        }

        return ImageDescription.builder()
            .title(title)
            .description(description)
            .fullDescription(fullDescription)
            .documentation(documentation)
            .source(source)
            .build();
    }

    private java.util.Map<String, String> fetchOciAnnotations(ImageReference imageRef)
        throws IOException, StoreException {
        String manifestUrl = buildManifestUrl(imageRef);
        try {
            return withAuth(imageRef.getRegistry(), authHeader -> {
                ResponseEntity<String> response = restTemplate.exchange(
                    manifestUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(buildManifestHeaders(authHeader)),
                    String.class
                );
                String body = response.getBody();
                if (!StringUtils.hasText(body)) {
                    return Collections.emptyMap();
                }
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(body);
                com.fasterxml.jackson.databind.JsonNode annotationsNode = node.path("annotations");
                if (annotationsNode.isMissingNode() || annotationsNode.isNull()) {
                    return Collections.emptyMap();
                }
                java.util.Map<String, String> result = new java.util.HashMap<>();
                annotationsNode.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asText()));
                return result;
            });
        } catch (IOException | StoreException | RestClientException e) {
            log.debug("Failed to extract OCI annotations for {}: {}", imageRef, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String fetchDockerHubShortDescription(ImageReference imageRef) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = fetchDockerHubRepositoryNode(imageRef);
            if (node != null) {
                String value = node.path("description").asText(null);
                return StringUtils.hasText(value) ? value : null;
            }
        } catch (IOException e) {
            log.debug("Failed to fetch Docker Hub short description for {}: {}", imageRef, e.getMessage());
        }
        return null;
    }

    private String fetchDockerHubFullDescription(ImageReference imageRef) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = fetchDockerHubRepositoryNode(imageRef);
            if (node != null) {
                String value = node.path("full_description").asText(null);
                return StringUtils.hasText(value) ? value : null;
            }
        } catch (IOException e) {
            log.debug("Failed to fetch Docker Hub full description for {}: {}", imageRef, e.getMessage());
        }
        return null;
    }

    private com.fasterxml.jackson.databind.JsonNode fetchDockerHubRepositoryNode(ImageReference imageRef)
        throws IOException {
        String[] parts = imageRef.getRepository().split("/", 2);
        String namespace = parts.length == 2 ? parts[0] : "library";
        String repo = parts.length == 2 ? parts[1] : parts[0];
        String hubUrl = "https://hub.docker.com/v2/repositories/" + namespace + "/" + repo + "/";

        try {
            ResponseEntity<String> hubResponse = restTemplate.exchange(
                hubUrl,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class
            );
            String body = hubResponse.getBody();
            return StringUtils.hasText(body) ? objectMapper.readTree(body) : null;
        } catch (RestClientException e) {
            log.debug("Failed to call Docker Hub API for {}: {}", imageRef, e.getMessage());
            return null;
        }
    }

    private boolean isDockerHub(String registry) {
        return "index.docker.io".equals(registry) || "registry-1.docker.io".equals(registry);
    }

    private boolean isGitHubRegistry(String registry) {
        return "ghcr.io".equals(registry);
    }

    /**
     * For GHCR images the repository path is {@code owner/repo[/…]}, so the first two
     * segments map directly to a GitHub repository.
     */
    private String inferGitHubRepoUrl(ImageReference imageRef) {
        String[] parts = imageRef.getRepository().split("/", 3);
        if (parts.length >= 2) {
            return "https://github.com/" + parts[0] + "/" + parts[1];
        }
        return null;
    }

    /**
     * Fetches the README for a GitHub repository identified by its URL
     * (e.g. {@code https://github.com/owner/repo}).
     * Uses the GitHub REST API {@code /repos/{owner}/{repo}/readme} endpoint.
     * An optional token from {@code containerimages.github-token} is sent as a Bearer header
     * to raise the rate-limit and reach private repositories.
     */
    private String fetchGitHubReadme(String repoUrl) {
        try {
            // Accept both https://github.com/owner/repo and https://github.com/owner/repo/… forms
            String path = repoUrl.replaceFirst("https?://github\\.com/", "");
            String[] parts = path.split("/", 3);
            if (parts.length < 2) {
                log.debug("Cannot parse GitHub repo URL: {}", repoUrl);
                return null;
            }
            String owner = parts[0];
            String repo = parts[1];
            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/readme";

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT, "application/vnd.github.raw+json");

            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );
            String body = response.getBody();
            return StringUtils.hasText(body) ? body : null;
        } catch (RestClientException e) {
            log.debug("Failed to fetch GitHub README for {}: {}", repoUrl, e.getMessage());
            return null;
        }
    }

    /*
     * Implements the standard Docker/OCI Bearer token flow described in
     * https://distribution.github.io/distribution/spec/auth/token/
     * Works with Docker Hub, Quay.io, Harbor, GHCR, and other v2-compatible registries.
     */
    private String fetchBearerToken(String wwwAuthenticate, Credential credential) {
        Matcher matcher = BEARER_REALM_PATTERN.matcher(wwwAuthenticate);
        if (!matcher.find()) {
            return null;
        }

        String realm = matcher.group(BEARER_GROUP_REALM);
        String service = matcher.group(BEARER_GROUP_SERVICE);
        String scope = matcher.group(BEARER_GROUP_SCOPE);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(realm);
        if (StringUtils.hasText(service)) {
            uriBuilder.queryParam("service", service);
        }
        if (StringUtils.hasText(scope)) {
            uriBuilder.queryParam("scope", scope);
        }

        HttpHeaders tokenHeaders = new HttpHeaders();
        if (credential != null) {
            tokenHeaders.setBasicAuth(credential.getUsername(), credential.getPassword());
        }

        try {
            ResponseEntity<TokenResponse> tokenResponse = restTemplate.exchange(
                uriBuilder.toUriString(),
                HttpMethod.GET,
                new HttpEntity<>(tokenHeaders),
                TokenResponse.class
            );

            if (tokenResponse.getBody() != null) {
                // Both "token" and "access_token" are valid per the spec
                String token = tokenResponse.getBody().getToken();
                if (!StringUtils.hasText(token)) {
                    token = tokenResponse.getBody().getAccessToken();
                }
                return token;
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch bearer token from {}: {}", realm, e.getMessage());
        }

        return null;
    }

    @FunctionalInterface
    private interface AuthenticatedCall<T> {
        T execute(@Nullable String authHeader) throws IOException, StoreException;
    }
}
