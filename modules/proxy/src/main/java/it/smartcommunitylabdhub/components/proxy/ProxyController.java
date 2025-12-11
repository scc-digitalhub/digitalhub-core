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

package it.smartcommunitylabdhub.components.proxy;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.RunManager;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Slf4j
public class ProxyController {

    @Autowired
    private ProxyService proxyService;

    @Autowired
    RunManager runManager;

    @RequestMapping(value = "/proxy/**")
    public ResponseEntity<String> handleProxyRequest(HttpServletRequest request) throws IOException {
        //custom header for request url forwarding
        String requestUrl = request.getHeader("X-Proxy-URL");

        log.info("Receive {} for url {}", request.getMethod(), requestUrl);

        ResponseEntity<String> response = proxyService.proxyRequest(request);

        //build response
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        MediaType contentType = response.getHeaders().getContentType();
        headers.add(HttpHeaders.CONTENT_TYPE, contentType.toString());
        return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
    }

    @RequestMapping(value = "/-/{project}/runs/{id}/proxy")
    public ResponseEntity<String> proxyRequest(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        HttpServletRequest request
    ) throws IOException {
        Run entity = runManager.getRun(id);

        //check for project and name match
        if (!entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        //custom header for request url forwarding
        String requestUrl = request.getHeader("X-Proxy-URL");
        if (requestUrl == null || requestUrl.isEmpty()) {
            throw new IllegalArgumentException("missing proxy url");
        }

        if (!requestUrl.startsWith("http://") && !requestUrl.startsWith("https://")) {
            requestUrl = "http://" + requestUrl;
        }

        String requestMethod = request.getHeader("X-Proxy-Method");
        if (requestMethod == null || requestMethod.isEmpty()) {
            requestMethod = "GET";
        }

        //check that run contains the same url
        if (entity.getStatus() == null) {
            throw new IllegalArgumentException("invalid run status");
        }

        K8sServiceStatus k8sService = K8sServiceStatus.with(entity.getStatus());
        if (k8sService == null || k8sService.getService() == null || k8sService.getService().getUrl() == null) {
            throw new IllegalArgumentException("invalid run service");
        }
        String serviceUrl = k8sService.getService().getUrl();
        if (serviceUrl == null || serviceUrl.isEmpty()) {
            throw new IllegalArgumentException("invalid run service url");
        }

        if (!serviceUrl.startsWith("http://") && !serviceUrl.startsWith("https://")) {
            serviceUrl = "http://" + serviceUrl;
        }

        //check for host match
        String requestHost = UriComponentsBuilder.fromUriString(requestUrl).build().getHost();
        String serviceHost = UriComponentsBuilder.fromUriString(serviceUrl).build().getHost();

        if (!requestHost.startsWith(serviceHost)) {
            throw new IllegalArgumentException("invalid destination host");
        }

        log.info("Receive {} for url {}", request.getMethod(), requestUrl);

        ResponseEntity<String> response = proxyService.proxyRequest(requestUrl, requestMethod, request);

        //build response
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        if (response.getHeaders() != null) {
            //keep content type
            MediaType contentType = response.getHeaders().getContentType() != null
                ? response.getHeaders().getContentType()
                : MediaType.TEXT_PLAIN;
            headers.add(HttpHeaders.CONTENT_TYPE, contentType.toString());

            //copy everything else as X-Proxy response
            response
                .getHeaders()
                .entrySet()
                .forEach(e -> {
                    String h = "X-Proxy-" + e.getKey();
                    headers.put(h, e.getValue());
                    //make sure all X-Proxy headers are exposed
                    headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, h);
                });

            //append status code
            if (response.getStatusCode() != null) {
                headers.add("X-Proxy-Status", response.getStatusCode().toString());
                //make sure all X-Proxy headers are exposed
                headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Proxy-Status");
            }
        }
        return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
    }
}
