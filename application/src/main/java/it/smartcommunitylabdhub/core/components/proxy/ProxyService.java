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

package it.smartcommunitylabdhub.core.components.proxy;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class ProxyService {

    private final RestTemplate restTemplate;

    public ProxyService() {
        this.restTemplate = new RestTemplate();
    }

    public ProxyService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<String> proxyRequest(HttpServletRequest request) throws IOException {
        String targetUrl = request.getHeader("X-Proxy-URL");
        if (targetUrl == null || targetUrl.isEmpty()) {
            throw new IllegalArgumentException("Missing X-Proxy-URL header");
        }
        String targetMethod = request.getHeader("X-Proxy-Method");
        if (targetMethod == null || targetMethod.isEmpty()) {
            targetMethod = "GET";
        }

        return proxyRequest(targetUrl, targetMethod, request);
    }

    public ResponseEntity<String> proxyRequest(String targetUrl, String targetMethod, HttpServletRequest request)
        throws IOException {
        log.debug("Proxying request to URL: {}", targetUrl);

        // Copy request body
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());

        // Copy headers except X-Proxy-URL and Authorization
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!headerName.toLowerCase().startsWith("x-proxy-")) {
                headers.put(headerName, Collections.list(request.getHeaders(headerName)));
            }
        }

        //forward client IP
        headers.add("X-Forwarded-For", request.getRemoteAddr());

        //make sure authorization is not forwarded
        headers.remove(HttpHeaders.AUTHORIZATION);

        //accept only json and text
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));

        // Build request entity
        HttpMethod method = HttpMethod.valueOf(targetMethod.toUpperCase());
        if (method == null) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + targetMethod);
        }
        HttpEntity<byte[]> httpEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = null;
        // Send request
        try {
            response = restTemplate.exchange(targetUrl, method, httpEntity, String.class);

            // Validate content type
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType == null) {
                throw new IllegalStateException("Missing or invalid Content-Type");
            }

            MediaType textType = new MediaType("text", "*");
            if (
                !MediaType.TEXT_PLAIN.includes(contentType) &&
                !MediaType.APPLICATION_JSON.includes(contentType) &&
                !MediaType.TEXT_HTML.includes(contentType) &&
                !textType.includes(contentType)
            ) {
                throw new IllegalStateException("Unsupported Content-Type: " + contentType);
            }
        } catch (HttpClientErrorException hte) {
            //catch exception and build response
            response = new ResponseEntity<>(hte.getStatusCode());

            // Validate content type
            MediaType contentType = response.getHeaders().getContentType();
            MediaType textType = new MediaType("text", "*");
            if (
                MediaType.TEXT_PLAIN.includes(contentType) ||
                MediaType.APPLICATION_JSON.includes(contentType) ||
                MediaType.TEXT_HTML.includes(contentType) ||
                textType.includes(contentType)
            ) {
                //collect body
                response = new ResponseEntity<>(response.getBody(), response.getHeaders(), response.getStatusCode());
            } else {
                //pass headers only
                response = new ResponseEntity<>(response.getHeaders(), response.getStatusCode());
            }
        }

        return response;
    }
}
