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

package it.smartcommunitylabdhub.console.controllers;

import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.console.ConsoleConfigProvider;
import it.smartcommunitylabdhub.console.Keys;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class ConsoleController {

    public static final String CONSOLE_CONTEXT = Keys.CONSOLE_CONTEXT;
    public static final String AUTH_PATH = "/api/auth";
    public static final String ENV_PREFIX = "REACT_APP_";

    private ApplicationProperties applicationProperties;
    private ConsoleConfigProvider configProvider;

    @Autowired
    private ConfigurationService configurationService;

    public ConsoleController(ApplicationProperties applicationProperties, SecurityProperties securityProperties) {
        this.applicationProperties = applicationProperties;
        configProvider = new ConsoleConfigProvider(applicationProperties, securityProperties);
    }

    @Autowired
    public void setClarityKey(@Value("${frontend.clarity.key}") String clarityKey) {
        if (this.configProvider != null) {
            this.configProvider.setClarityKey(clarityKey);
        }
    }

    @GetMapping(value = { "/", CONSOLE_CONTEXT })
    public ModelAndView root() {
        return new ModelAndView("redirect:" + CONSOLE_CONTEXT + "/");
    }

    // @GetMapping(value = { CONSOLE_CONTEXT, CONSOLE_CONTEXT + "/**" })
    @GetMapping(
        value = {
            CONSOLE_CONTEXT + "/",
            CONSOLE_CONTEXT + "/{path:^(?!\\S+(?:\\.[a-z0-9]{2,}))\\S+$}",
            CONSOLE_CONTEXT + "/-/**",
        }
    )
    public String console(Model model, HttpServletRequest request) {
        // build config
        Map<String, String> config = new HashMap<>();

        config.put("VITE_APP_NAME", applicationProperties.getDescription());
        config.put("REACT_APP_VERSION", applicationProperties.getVersion());

        //add console config
        if (configProvider != null && configProvider.getConfig() != null) {
            configProvider
                .getConfig()
                .toMap()
                .forEach((k, v) -> {
                    if (v != null) {
                        config.put(k.toUpperCase(), v.toString());
                    }
                });
        }

        //dump all configurations prefixed
        if (configurationService != null) {
            configurationService
                .getConfigurations()
                .forEach(c -> {
                    c
                        .toMap()
                        .forEach((k, v) -> {
                            if (v != null) {
                                String vx = v.toString();
                                if (v instanceof Collection<?> coll) {
                                    vx = String.join(",", coll.stream().map(Object::toString).toList());
                                }
                                config.put(ENV_PREFIX + k.toUpperCase(), vx);
                            }
                        });
                });
        }

        model.addAttribute("config", config);
        return "console.html";
    }

    @RequestMapping(value = AUTH_PATH, method = { RequestMethod.GET, RequestMethod.POST })
    public ResponseEntity<User> auth(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.internalServerError().build();
        }

        User user = new User(
            auth.getName(),
            auth
                .getAuthorities()
                .stream()
                .map(a -> a.getAuthority())
                .toList()
        );
        return ResponseEntity.ok(user);
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public class User {

        private String username;
        private List<String> permissions;
    }
}
