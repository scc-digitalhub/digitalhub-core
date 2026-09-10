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

package it.smartcommunitylabdhub.core.config.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Audit trail of the API and auth endpoints: one JSON line per request on the
 * {@code AUDIT_LOGGER} logger.
 *
 * The caller identity is taken exclusively from the Spring Security context, i.e. from
 * the authentication already validated by the security filter chains. Tokens carried by
 * the request (Authorization header, token exchange parameters) are never parsed here:
 * their signature is not verified at this stage, so their claims cannot be trusted.
 *
 * The response is not buffered: only its status code is recorded.
 */
@Component
@Slf4j
public class AuditLoggingFilter extends OncePerRequestFilter {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT_LOGGER");

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            logAuditInfo(wrappedRequest, response);
        }
    }

    private void logAuditInfo(ContentCachingRequestWrapper request, HttpServletResponse response) {
        String uri = request.getRequestURI();

        // Filter out noisy endpoints
        if (
            uri.startsWith("/actuator") ||
            uri.startsWith("/swagger-ui") ||
            uri.startsWith("/v3/api-docs") ||
            uri.startsWith("/h2-console")
        ) {
            return;
        }

        // Restrict to api and auth endpoints
        if (!(uri.contains("/api/") || uri.contains("/auth/") || uri.startsWith("/api/") || uri.startsWith("/auth/"))) {
            return;
        }

        try {
            Map<String, Object> auditInfo = new LinkedHashMap<>();
            auditInfo.put("timestamp", Instant.now().toString());

            // User that performed the action, as established by the security filter chain
            String user = "anonymous";
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                user = auth.getName();
            }
            auditInfo.put("user", user);

            String email = extractEmail(auth);
            if (email != null) {
                auditInfo.put("email", email);
            }

            // What type of action did he perform
            auditInfo.put("actionType", request.getMethod());

            // To what system this action will be consumed or triggered
            auditInfo.put("system", uri);

            // Additional useful info
            auditInfo.put("status", response.getStatus());
            auditInfo.put("clientIp", request.getRemoteAddr());

            // Log POST/PUT/PATCH payload
            if (
                "POST".equalsIgnoreCase(request.getMethod()) ||
                "PUT".equalsIgnoreCase(request.getMethod()) ||
                "PATCH".equalsIgnoreCase(request.getMethod())
            ) {
                byte[] content = request.getContentAsByteArray();
                if (content.length > 0) {
                    String payload = new String(content, StandardCharsets.UTF_8);
                    try {
                        // Attempt to parse as JSON for cleaner logging if possible
                        Object jsonPayload = objectMapper.readValue(payload, Object.class);
                        auditInfo.put("payload", maskSensitiveData(jsonPayload));
                    } catch (Exception e) {
                        auditInfo.put("payload", maskSensitiveString(payload));
                    }
                }
            }

            String jsonLog = objectMapper.writeValueAsString(auditInfo);

            // Log to standard output with standard log format
            auditLog.info(jsonLog);
        } catch (Exception e) {
            log.error("Failed to generate audit log", e);
        }
    }

    /**
     * Resolve the email of the authenticated user from the security context only.
     * Returns null when the request is anonymous or the authentication carries no email.
     */
    private String extractEmail(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }

        Authentication actualAuth = auth;

        // Custom DHUB Authentication extraction
        if (auth instanceof UserAuthentication<?> dhubAuth) {
            try {
                actualAuth = dhubAuth.getToken();
            } catch (Exception e) {
                // Ignore and fallback
            }
        }

        // Spring Security Pattern Matching extraction
        String email = null;
        Object principal = actualAuth.getPrincipal();

        if (actualAuth instanceof JwtAuthenticationToken jwtAuth) {
            email = jwtAuth.getToken().getClaimAsString("email");
        } else if (
            actualAuth instanceof BearerTokenAuthentication bearerAuth &&
            bearerAuth.getPrincipal() instanceof OAuth2AuthenticatedPrincipal oauth2Principal
        ) {
            email = oauth2Principal.getAttribute("email");
        } else if (principal instanceof Jwt jwtPrincipal) {
            email = jwtPrincipal.getClaimAsString("email");
        } else if (principal instanceof OidcUser oidcUser) {
            email = oidcUser.getEmail();
        } else if (principal instanceof OAuth2User oauth2User) {
            email = oauth2User.getAttribute("email");
        }

        // Fallback: if username itself contains '@'
        if (email == null && auth.getName() != null && auth.getName().contains("@")) {
            email = auth.getName();
        }

        return email;
    }

    private Object maskSensitiveData(Object object) {
        if (object == null) return null;
        if (object instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) object;
            Map<String, Object> maskedMap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (isSensitiveKey(key)) {
                    maskedMap.put(key, "*****");
                } else {
                    maskedMap.put(key, maskSensitiveData(val));
                }
            }
            return maskedMap;
        } else if (object instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) object;
            List<Object> maskedList = new ArrayList<>();
            for (Object item : list) {
                maskedList.add(maskSensitiveData(item));
            }
            return maskedList;
        }
        return object;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return (
            lower.contains("password") ||
            lower.contains("secret") ||
            lower.contains("token") ||
            lower.contains("credential") ||
            lower.contains("auth") ||
            lower.contains("key") ||
            lower.contains("passphrase") ||
            lower.contains("private")
        );
    }

    private String maskSensitiveString(String payload) {
        if (payload == null) return null;
        return payload.replaceAll(
            "(?i)(\"\\w*(?:password|secret|token|credential|auth|key|private)\\w*\"\\s*:\\s*\")[^\"]*(\")",
            "$1*****$2"
        );
    }
}
