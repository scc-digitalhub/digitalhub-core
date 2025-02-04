package it.smartcommunitylabdhub.core.config;

import it.smartcommunitylabdhub.authorization.AuthenticationManager;
import it.smartcommunitylabdhub.authorization.config.KeyStoreConfig;
import it.smartcommunitylabdhub.authorization.services.AuthorizableAwareEntityService;
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties.JwtAuthenticationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties.OidcAuthenticationProperties;
import it.smartcommunitylabdhub.commons.models.project.Project;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@Order(16)
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@Slf4j
public class SecurityConfig {

    public static final String API_PREFIX = "/api";

    @Autowired
    ApplicationProperties applicationProperties;

    @Autowired
    SecurityProperties properties;

    @Value("${security.api.cors.origins}")
    private String corsOrigins;

    @Value("${management.server.port}")
    private int managementPort;

    @Value("${management.endpoints.web.base-path}")
    private String managementBasePath;

    @Value("${jwt.client-id}")
    private String clientId;

    @Value("${jwt.client-secret}")
    private String clientSecret;

    @Autowired
    KeyStoreConfig keyStoreConfig;

    @Autowired(required = false)
    JwtTokenService jwtTokenService;

    @Autowired
    AuthorizableAwareEntityService<Project> projectAuthHelper;

    @Bean("apiSecurityFilterChain")
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        HttpSecurity securityChain = http
            .securityMatcher(getApiRequestMatcher())
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(getApiRequestMatcher()).hasRole("USER").anyRequest().authenticated();
            })
            // disable request cache
            .requestCache(requestCache -> requestCache.disable())
            //disable csrf
            .csrf(csrf -> csrf.disable())
            // we don't want a session for these endpoints, each request should be evaluated
            .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // allow cors
        securityChain.cors(cors -> {
            if (StringUtils.hasText(corsOrigins)) {
                cors.configurationSource(corsConfigurationSource(corsOrigins));
            } else {
                cors.disable();
            }
        });

        //authentication (when configured)
        if (properties.isRequired() && jwtTokenService != null) {
            List<AuthenticationProvider> authProviders = new ArrayList<>();

            // always enable internal jwt auth provider
            JwtAuthenticationProvider coreJwtAuthProvider = new JwtAuthenticationProvider(jwtTokenService.getDecoder());
            coreJwtAuthProvider.setJwtAuthenticationConverter(jwtTokenService.getAuthenticationConverter());
            authProviders.add(coreJwtAuthProvider);

            //enable basic if required
            if (properties.isBasicAuthEnabled()) {
                DaoAuthenticationProvider daoProvider = new DaoAuthenticationProvider();
                daoProvider.setUserDetailsService(
                    userDetailsService(properties.getBasic().getUsername(), properties.getBasic().getPassword())
                );
                daoProvider.setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());
                authProviders.add(daoProvider);
            }

            // Create authentication Manager
            AuthenticationManager authManager = new AuthenticationManager(authProviders);
            securityChain.authenticationManager(authManager);
            securityChain.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.authenticationManager(authManager)));

            //enable basic if required
            //NOTE: we need to it now to use our authenticationManager
            if (properties.isBasicAuthEnabled()) {
                securityChain
                    .httpBasic(basic -> basic.authenticationEntryPoint(new Http403ForbiddenEntryPoint()))
                    .userDetailsService(
                        userDetailsService(properties.getBasic().getUsername(), properties.getBasic().getPassword())
                    );
            }

            //disable anonymous
            securityChain.anonymous(anon -> anon.disable());
        } else {
            //assign both USER and ADMIN to anon user to bypass all scoped permission checks
            securityChain.anonymous(anon -> {
                anon.authorities("ROLE_USER", "ROLE_ADMIN");
                anon.principal("anonymous");
            });
        }

        securityChain.exceptionHandling(handling -> {
            handling
                .authenticationEntryPoint(new Http403ForbiddenEntryPoint())
                .accessDeniedHandler(new AccessDeniedHandlerImpl()); // use 403
        });

        return securityChain.build();
    }

    @Bean("authSecurityFilterChain")
    public SecurityFilterChain authSecurityFilterChain(HttpSecurity http) throws Exception {
        //token chain
        HttpSecurity securityChain = http
            .securityMatcher(new AntPathRequestMatcher("/auth/token"))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(getAuthRequestMatcher()).hasRole("USER").anyRequest().authenticated();
            })
            // disable request cache
            .requestCache(requestCache -> requestCache.disable())
            //disable csrf
            .csrf(csrf -> csrf.disable())
            // disable session for token requests
            .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // allow cors
        securityChain.cors(cors -> {
            if (StringUtils.hasText(corsOrigins)) {
                cors.configurationSource(corsConfigurationSource(corsOrigins));
            } else {
                cors.disable();
            }
        });

        //enable anonymous auth, we'll double check auth in granters
        securityChain.anonymous(anon -> anon.authorities(List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        //enable basic if required (client auth)
        //NOTE: configure first to avoid injecting user auth manager for basic
        if (StringUtils.hasText(clientId) && StringUtils.hasText(clientId)) {
            //client basic auth flow
            securityChain
                .httpBasic(basic -> basic.authenticationEntryPoint(new Http403ForbiddenEntryPoint()))
                .userDetailsService(userDetailsService(clientId, clientSecret));
        }

        securityChain.exceptionHandling(handling -> {
            handling
                .authenticationEntryPoint(new Http403ForbiddenEntryPoint())
                .accessDeniedHandler(new AccessDeniedHandlerImpl()); // use 403
        });

        return securityChain.build();
    }

    @Bean("authorizeSecurityFilterChain")
    public SecurityFilterChain authorizeSecurityFilterChain(HttpSecurity http) throws Exception {
        //token chain
        HttpSecurity securityChain = http
            .securityMatcher(getAuthRequestMatcher())
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(getAuthRequestMatcher()).hasRole("USER").anyRequest().authenticated();
            })
            // enable request cache IN SESSION
            .requestCache(requestCache -> requestCache.requestCache(new HttpSessionRequestCache()))
            //disable csrf
            .csrf(csrf -> csrf.disable())
            // we need session to handle auth flows
            .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.ALWAYS));

        // allow cors
        securityChain.cors(cors -> {
            if (StringUtils.hasText(corsOrigins)) {
                cors.configurationSource(corsConfigurationSource(corsOrigins));
            } else {
                cors.disable();
            }
        });

        //disable anonymous auth, to authenticate we *need* valid credentials!
        securityChain.anonymous(anon -> anon.disable());

        //enable upstream oidc
        if (properties.isOidcAuthEnabled()) {
            OidcAuthenticationProperties props = properties.getOidc();
            //we support a single static client
            String registrationId = "oidc";
            ClientRegistration.Builder client = ClientRegistrations
                .fromIssuerLocation(props.getIssuerUri())
                .registrationId(registrationId)
                .clientName(props.getClientName())
                .clientId(props.getClientId())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/auth/code/" + registrationId)
                .scope(props.getScope())
                .userNameAttributeName(
                    StringUtils.hasText(props.getUsernameAttributeName())
                        ? props.getUsernameAttributeName()
                        : IdTokenClaimNames.SUB
                );

            if (StringUtils.hasText(props.getClientSecret())) {
                //use secret
                client
                    .clientSecret(clientSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            } else {
                //use PKCE
                client.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
            }

            InMemoryClientRegistrationRepository repository = new InMemoryClientRegistrationRepository(client.build());

            //register provider for authorize chain
            securityChain.oauth2Login(oauth2 -> {
                oauth2.clientRegistrationRepository(repository);
                oauth2.authorizationEndpoint(endpoint -> endpoint.baseUri("/auth/authorization"));
                oauth2.redirectionEndpoint(endpoint -> endpoint.baseUri("/auth/code/*"));
                oauth2.userInfoEndpoint(userInfo ->
                    userInfo.userAuthoritiesMapper(authorities ->
                        Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))
                    )
                );
            });
            // //add entryPoint towards provider
            // securityChain.exceptionHandling(handling -> {
            //     handling
            //         .authenticationEntryPoint(
            //             new LoginUrlAuthenticationEntryPoint("/auth/authorization/" + registrationId)
            //         )
            //         .accessDeniedHandler(new AccessDeniedHandlerImpl()); // use 403
            // });
        }

        return securityChain.build();
    }

    @Bean("h2SecurityFilterChain")
    public SecurityFilterChain h2SecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher(new AntPathRequestMatcher("/h2-console/**"))
            .authorizeHttpRequests(auth -> {
                auth.anyRequest().permitAll();
            })
            //disable csrf
            .csrf(csrf -> csrf.disable())
            // we don't want a session for these endpoints, each request should be evaluated
            .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // enable frame options
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .build();
    }

    @Bean("coreSecurityFilterChain")
    public SecurityFilterChain coreSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> {
                auth.anyRequest().permitAll();
            })
            // disable request cache
            .requestCache(requestCache -> requestCache.disable())
            .build();
    }

    @Bean("monitoringSecurityFilterChain")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // match only actuator endpoints
        return http
            .securityMatcher(getManagementRequestMatcher())
            .authorizeHttpRequests(auth -> {
                auth.anyRequest().permitAll();
            })
            .exceptionHandling(handling -> handling.authenticationEntryPoint(new Http403ForbiddenEntryPoint()))
            .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .build();
    }

    public RequestMatcher getManagementRequestMatcher() {
        return (HttpServletRequest request) -> managementPort == request.getLocalPort();
    }

    public RequestMatcher getApiRequestMatcher() {
        return new AntPathRequestMatcher(API_PREFIX + "/**");
    }

    public RequestMatcher getAuthRequestMatcher() {
        return new AntPathRequestMatcher("/auth/**");
    }

    private CorsConfigurationSource corsConfigurationSource(String origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(new ArrayList<>(StringUtils.commaDelimitedListToSet(origins)));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("authorization", "range"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication()).map(a -> a.getName());
    }

    /**
     * Internal basic auth
     */
    public static UserDetailsService userDetailsService(String username, String password) {
        //create admin user with full permissions
        UserDetails admin = User
            .withDefaultPasswordEncoder()
            .username(username)
            .password(password)
            .roles("ADMIN", "USER")
            // .roles("USER")
            .build();

        return new InMemoryUserDetailsManager(admin);
    }

    /**
     * Internal auth via JWT
     */

    // public static JwtDecoder coreJwtDecoder(String issuer, String audience, JWK jwk) throws JOSEException {
    //     //we support only RSA keys
    //     if (!(jwk instanceof RSAKey)) {
    //         throw new IllegalArgumentException("the provided key is not suitable for token authentication");
    //     }

    //     NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withPublicKey(jwk.toRSAKey().toRSAPublicKey()).build();

    //     OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);

    //     OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
    //         JwtClaimNames.AUD,
    //         (aud -> aud != null && aud.contains(audience))
    //     );

    //     //access tokens *do not contain* at_hash, those are refresh
    //     OAuth2TokenValidator<Jwt> accessTokenValidator = new JwtClaimValidator<String>(
    //         IdTokenClaimNames.AT_HASH,
    //         (Objects::isNull)
    //     );

    //     OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
    //         withIssuer,
    //         audienceValidator,
    //         accessTokenValidator
    //     );
    //     jwtDecoder.setJwtValidator(validator);

    //     return jwtDecoder;
    // }

    // public static JwtAuthenticationConverter coreJwtAuthenticationConverter(
    //     String claim,
    //     AuthorizableAwareEntityService<Project> projectAuthHelper
    // ) {
    //     JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    //     converter.setJwtGrantedAuthoritiesConverter((Jwt source) -> {
    //         if (source == null) return null;

    //         Set<GrantedAuthority> authorities = new HashSet<>();
    //         authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

    //         if (StringUtils.hasText(claim) && source.hasClaim(claim)) {
    //             List<String> roles = source.getClaimAsStringList(claim);
    //             if (roles != null) {
    //                 roles.forEach(r -> {
    //                     //use as is
    //                     authorities.add(new SimpleGrantedAuthority(r));
    //                 });
    //             }
    //         }

    //         //refresh project authorities via helper
    //         if (projectAuthHelper != null && StringUtils.hasText(source.getSubject())) {
    //             String username = source.getSubject();

    //             //inject roles from ownership of projects
    //             projectAuthHelper
    //                 .findIdsByCreatedBy(username)
    //                 .forEach(p -> {
    //                     //derive a scoped ADMIN role
    //                     authorities.add(new SimpleGrantedAuthority(p + ":ROLE_ADMIN"));
    //                 });

    //             //inject roles from sharing of projects
    //             projectAuthHelper
    //                 .findIdsBySharedTo(username)
    //                 .forEach(p -> {
    //                     //derive a scoped USER role
    //                     //TODO make configurable?
    //                     authorities.add(new SimpleGrantedAuthority(p + ":ROLE_USER"));
    //                 });
    //         }

    //         return authorities;
    //     });
    //     return converter;
    // }

    /**
     * External auth via JWT
     * TODO move config to service
     */
    public static JwtDecoder externalJwtDecoder(String issuer, String audience) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();

        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD,
            (aud -> aud != null && aud.contains(audience))
        );

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
        jwtDecoder.setJwtValidator(withAudience);

        return jwtDecoder;
    }

    public static Converter<Jwt, AbstractAuthenticationToken> externalJwtAuthenticationConverter(
        String usernameClaimName,
        String rolesClaimName,
        AuthorizableAwareEntityService<Project> projectAuthHelper
    ) {
        return (Jwt jwt) -> {
            Set<GrantedAuthority> authorities = new HashSet<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

            //read roles from token
            if (StringUtils.hasText(rolesClaimName) && jwt.hasClaim(rolesClaimName)) {
                List<String> roles = jwt.getClaimAsStringList(rolesClaimName);
                if (roles != null) {
                    roles.forEach(r -> {
                        if ("ROLE_ADMIN".equals(r) || r.contains(":")) {
                            //use as is
                            authorities.add(new SimpleGrantedAuthority(r));
                        } else {
                            //derive a scoped USER role
                            authorities.add(new SimpleGrantedAuthority(r + ":ROLE_USER"));
                        }
                    });
                }
            }

            //principalName
            String username = jwt.getClaimAsString(usernameClaimName);

            //fallback to SUB if missing
            if (!StringUtils.hasText(username)) {
                username = jwt.getSubject();
            }

            if (projectAuthHelper != null) {
                //inject roles from ownership of projects
                //derive a scoped ADMIN role
                projectAuthHelper
                    .findIdsByCreatedBy(username)
                    .forEach(p -> authorities.add(new SimpleGrantedAuthority(p + ":ROLE_ADMIN")));

                //inject roles from sharing of projects
                //derive a scoped USER role
                //TODO make configurable?
                projectAuthHelper
                    .findIdsBySharedTo(username)
                    .forEach(p -> authorities.add(new SimpleGrantedAuthority(p + ":ROLE_USER")));
            }

            return new JwtAuthenticationToken(jwt, authorities, username);
        };
    }
}
