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

package it.smartcommunitylabdhub.core.config;

import it.smartcommunitylabdhub.authorization.UserAuthenticationManager;
import it.smartcommunitylabdhub.authorization.UserAuthenticationManagerBuilder;
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import it.smartcommunitylabdhub.core.websocket.UserNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.eclipse.jetty.http.HttpHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.www.BasicAuthenticationConverter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String WS_PATH = "/ws";

    //TODO completare configurazione (es. timeout)
    @Value("${security.api.cors.origins}")
    private String corsOrigins;

    @Autowired
    ApplicationProperties applicationProperties;

    @Autowired
    SecurityProperties securityProperties;

    @Autowired(required = false)
    JwtTokenService jwtTokenService;

    @Autowired
    UserAuthenticationManagerBuilder authenticationManagerBuilder;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        //endpoint for a client to connect for the handshake
        StompWebSocketEndpointRegistration endpoint = registry.addEndpoint(WS_PATH);
        if (StringUtils.hasText(corsOrigins)) {
            Set<String> origins = StringUtils.commaDelimitedListToSet(corsOrigins);
            endpoint.setAllowedOrigins(origins.toArray(String[]::new));
        }
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        //make sure messages are delivered in-order
        config.setPreservePublishOrder(true);

        //enable broker for calls and notifications
        config.enableSimpleBroker(SecurityConfig.API_PREFIX, UserNotificationService.PREFIX);
        config.setApplicationDestinationPrefixes(SecurityConfig.API_PREFIX);
        config.setUserDestinationPrefix(UserNotificationService.USER_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        if (securityProperties.isRequired()) {
            // auth converters
            List<AuthenticationConverter> converters = new ArrayList<>();
            List<AuthenticationProvider> providers = new ArrayList<>();

            if (securityProperties.isBasicAuthEnabled()) {
                //basic auth converter
                converters.add(new BasicAuthenticationConverter());

                //basic auth provider
                DaoAuthenticationProvider basicAuthProvider = new DaoAuthenticationProvider();
                basicAuthProvider.setUserDetailsService(
                    SecurityConfig.userDetailsService(
                        securityProperties.getBasic().getUsername(),
                        securityProperties.getBasic().getPassword()
                    )
                );
                providers.add(basicAuthProvider);
            }

            if (securityProperties.isOidcAuthEnabled() && jwtTokenService != null) {
                // bearer auth converter
                AuthenticationConverter converter = new AuthenticationConverter() {
                    private final BearerTokenResolver resolver = new DefaultBearerTokenResolver();

                    @Override
                    public Authentication convert(HttpServletRequest request) {
                        String bearer = resolver.resolve(request);
                        if (bearer == null) {
                            return null;
                        }

                        return new BearerTokenAuthenticationToken(bearer);
                    }
                };
                converters.add(converter);

                //jwt auth provider
                JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtTokenService.getDecoder());
                provider.setJwtAuthenticationConverter(jwtTokenService.getAuthenticationConverter());
                providers.add(provider);
            }

            UserAuthenticationManager authManager = authenticationManagerBuilder.build(providers);
            AuthInterceptor authInterceptor = new AuthInterceptor(authManager);
            authInterceptor.setConverters(converters);
            registration.interceptors(authInterceptor);
        }
    }

    public class AuthInterceptor implements ChannelInterceptor {

        private List<AuthenticationConverter> converters = Collections.emptyList();

        private final AuthenticationManager authManager;

        public AuthInterceptor(AuthenticationManager authManager) {
            Assert.notNull(authManager, "authentication manager can not be null");
            this.authManager = authManager;
        }

        public void setConverters(List<AuthenticationConverter> converters) {
            this.converters = converters;
        }

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

            //inject auth token on connect, will be propagated to ws session
            if (StompCommand.CONNECT == accessor.getCommand()) {
                // //figure out auth type, or anonymous
                // AbstractAuthenticationToken token = new AnonymousAuthenticationToken(
                //     "anonymous",
                //     "anonymous",
                //     Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                // );

                String header = accessor.getFirstNativeHeader(HttpHeader.AUTHORIZATION.asString());
                if (header != null) {
                    //mock request
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.addHeader(HttpHeader.AUTHORIZATION.asString(), header);

                    //fetch token from converters
                    Authentication auth = converters
                        .stream()
                        .map(c -> c.convert(request))
                        .filter(t -> t != null)
                        .findFirst()
                        .orElse(null);

                    if (auth != null) {
                        //validate
                        Authentication authentication = authManager.authenticate(auth);

                        //inject
                        accessor.setHeader("simpUser", authentication);
                        accessor.setUser(authentication);
                    }
                    //     //basic
                    //     token = basicAuthenticationConverter.convert(request);

                    //     if (token == null) {
                    //         //bearer
                    //         String bearer = bearerTokenResolver.resolve(request);
                    //         if (bearer != null) {
                    //             token = new BearerTokenAuthenticationToken(bearer);
                    //         }
                    //     }
                }
                // accessor.setUser(token);

                // write
                // accessor.setHeader("simpUser", auth);
            }

            return message;
        }
    }
}
