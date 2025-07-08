/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

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

package it.smartcommunitylabdhub.authorization.controllers;

import com.nimbusds.jwt.SignedJWT;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import java.text.ParseException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class UserInfoEndpoint {

    public static final String USERINFO_URL = "/auth/userinfo";

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired(required = false)
    private JwtTokenService jwtTokenService;

    @RequestMapping(value = USERINFO_URL, method = { RequestMethod.POST, RequestMethod.GET })
    public Map<String, Object> userinfo(
        @RequestParam Map<String, String> parameters,
        @CurrentSecurityContext SecurityContext securityContext
    ) {
        if (!securityProperties.isOidcAuthEnabled() || jwtTokenService == null) {
            throw new UnsupportedOperationException();
        }

        Authentication authentication = securityContext.getAuthentication();

        //resolve user authentication
        if (
            authentication == null ||
            !(authentication.isAuthenticated()) ||
            !(authentication instanceof UserAuthentication)
        ) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }
        try {
            UserAuthentication<?> user = (UserAuthentication<?>) authentication;
            log.debug("read userinfo for {}", user.getUsername());

            //fetch token
            SignedJWT token = jwtTokenService.generateAccessToken(user);
            Map<String, Object> claims;

            claims = token.getJWTClaimsSet().getClaims();

            if (log.isTraceEnabled()) {
                log.trace("userinfo: {}", claims);
            }

            return claims;
        } catch (ParseException e) {
            throw new IllegalArgumentException();
        }
    }
}
