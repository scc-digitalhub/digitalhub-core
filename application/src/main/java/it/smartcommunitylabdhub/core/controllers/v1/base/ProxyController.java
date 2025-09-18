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

package it.smartcommunitylabdhub.core.controllers.v1.base;

import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.components.proxy.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.HttpHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/proxy")
@ApiVersion("v1")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Slf4j
public class ProxyController {

    @Autowired
    private ProxyService proxyService;

    @RequestMapping(value = "/**")
    public ResponseEntity<String> handleProxyRequest(HttpServletRequest request) throws IOException {
        //custom header for request url forwarding
        String requestUrl = request.getHeader("X-Proxy-URL");

        log.info("Receive {} for url {}", request.getMethod(), requestUrl);

        ResponseEntity<String> response = proxyService.proxyRequest(request);

        //build response
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        MediaType contentType = response.getHeaders().getContentType();
        headers.add(HttpHeader.CONTENT_TYPE.asString(), contentType.toString());
        return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
    }
}
