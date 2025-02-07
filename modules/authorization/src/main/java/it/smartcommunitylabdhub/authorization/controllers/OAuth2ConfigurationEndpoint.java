package it.smartcommunitylabdhub.authorization.controllers;

import it.smartcommunitylabdhub.authorization.model.OpenIdConfig;
import it.smartcommunitylabdhub.authorization.model.OpenIdConfig.OpenIdConfigBuilder;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OAuth2ConfigurationEndpoint implements ConfigurationProvider {

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private SecurityProperties securityProperties;

    @Value("${jwt.cache-control}")
    private String cacheControl;

    private OpenIdConfig config = null;

    @GetMapping(value = { "/.well-known/openid-configuration", "/.well-known/oauth-authorization-server" })
    public ResponseEntity<Map<String, Serializable>> getConfiguration() {
        if (!securityProperties.isRequired()) {
            throw new UnsupportedOperationException();
        }

        if (config == null) {
            config = generate();
        }

        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, cacheControl).body(config.toMap());
    }

    private OpenIdConfig generate() {
        /*
         * OpenID Provider Metadata
         * https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata
         */

        String baseUrl = applicationProperties.getEndpoint();
        OpenIdConfigBuilder builder = OpenIdConfig.builder();

        builder.issuer(baseUrl);
        builder.jwksUri(baseUrl + JWKSEndpoint.JWKS_URL);
        builder.responseTypesSupported(Set.of("code"));

        List<String> grantTypes = Stream
            .of(
                AuthorizationGrantType.CLIENT_CREDENTIALS,
                AuthorizationGrantType.REFRESH_TOKEN,
                AuthorizationGrantType.TOKEN_EXCHANGE
            )
            .map(t -> t.getValue())
            .toList();

        if (securityProperties.isOidcAuthEnabled()) {
            grantTypes =
                Stream
                    .of(
                        AuthorizationGrantType.CLIENT_CREDENTIALS,
                        AuthorizationGrantType.REFRESH_TOKEN,
                        AuthorizationGrantType.AUTHORIZATION_CODE,
                        AuthorizationGrantType.TOKEN_EXCHANGE
                    )
                    .map(t -> t.getValue())
                    .toList();

            builder.authorizationEndpoint(baseUrl + AuthorizationEndpoint.AUTHORIZE_URL);
            builder.userinfoEndpoint(baseUrl + UserInfoEndpoint.USERINFO_URL);
        }

        builder.grantTypesSupported(new HashSet<>(grantTypes));
        builder.scopesSupported(Set.of("openid", "profile", "credentials", "offline_access"));

        builder.tokenEndpoint(baseUrl + TokenEndpoint.TOKEN_URL);
        Set<String> authMethods = Set.of("client_secret_basic", "client_secret_post", "none");
        builder.tokenEndpointAuthMethodsSupported(authMethods);

        return builder.build();
    }

    @Override
    @Nullable
    public OpenIdConfig getConfig() {
        if (config == null) {
            config = generate();
        }

        return config;
    }
}
