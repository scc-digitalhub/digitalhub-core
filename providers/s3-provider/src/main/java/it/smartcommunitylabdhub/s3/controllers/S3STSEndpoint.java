package it.smartcommunitylabdhub.s3.controllers;

import it.smartcommunitylabdhub.authorization.UserAuthenticationManager;
import it.smartcommunitylabdhub.authorization.UserAuthenticationManagerBuilder;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.s3.credentials.S3AssumeRoleProvider;
import it.smartcommunitylabdhub.s3.credentials.S3StaticCredentials;
import java.io.Serial;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityResponse;
import software.amazon.awssdk.services.sts.model.AssumedRoleUser;
import software.amazon.awssdk.services.sts.model.Credentials;

@RestController
@Slf4j
@ConditionalOnBean(S3AssumeRoleProvider.class)
public class S3STSEndpoint implements InitializingBean {

    public static final String STS_URL = "/auth/s3/sts";
    public static final String AUDIENCE = "s3";

    private static final String ACTION = "AssumeRoleWithWebIdentity";
    private static final String VERSION = "2011-06-15";
    private static final String DEFAULT_SESSION_NAME = "dhcore";
    private static final String XMLNS = "https://sts.amazonaws.com/doc/2011-06-15/";

    //the STS makes sense only for AssumeRole, we implement AssumeRoleWithWebIdentity
    private S3AssumeRoleProvider s3CredentialsProvider;
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserAuthenticationManagerBuilder authenticationManagerBuilder;

    private UserAuthenticationManager authManager;

    @Autowired(required = false)
    public void setS3CredentialsProvider(S3AssumeRoleProvider s3CredentialsProvider) {
        this.s3CredentialsProvider = s3CredentialsProvider;
    }

    @Autowired(required = false)
    public void setJwtTokenService(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //build the authentication manager

        if (jwtTokenService != null) {
            // enable internal jwt auth provider
            String audience = jwtTokenService.getAudience() + "/" + AUDIENCE;
            OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                (aud -> aud != null && aud.contains(audience))
            );
            JwtDecoder decoder = jwtTokenService.buildJwtDecoder(jwtTokenService.getJwk(), audienceValidator);
            JwtAuthenticationProvider coreJwtAuthProvider = new JwtAuthenticationProvider(decoder);
            coreJwtAuthProvider.setJwtAuthenticationConverter(jwtTokenService.getAuthenticationConverter());
            this.authManager = authenticationManagerBuilder.build(coreJwtAuthProvider);
        }
    }

    @RequestMapping(
        path = STS_URL,
        method = { RequestMethod.GET, RequestMethod.POST },
        consumes = { MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE },
        produces = MediaType.APPLICATION_XML_VALUE
    )
    public @ResponseBody String exchange(
        @RequestParam(name = "Action", required = false, defaultValue = ACTION) String action,
        @RequestParam(name = "RoleArn", required = false) String roleArn,
        @RequestParam(name = "RoleSessionName", required = false) String roleSessionName,
        @RequestParam(name = "WebIdentityToken", required = false) String webIdentityToken
    ) {
        if (s3CredentialsProvider == null || authManager == null) {
            throw new UnsupportedOperationException("sts service not available");
        }

        if (!ACTION.equals(action)) {
            throw new InvalidActionException(action);
        }

        if (!StringUtils.hasText(webIdentityToken)) {
            throw new InsufficientAuthenticationException("Missing web identity token");
        }

        Authentication auth = new BearerTokenAuthenticationToken(webIdentityToken);
        UserAuthentication<?> userAuthentication = authManager.authenticate(auth);

        if (userAuthentication == null || !userAuthentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication failed");
        }

        S3StaticCredentials credentials = s3CredentialsProvider.get(userAuthentication);
        if (credentials == null) {
            throw new InsufficientAuthenticationException("Invalid or missing credentials");
        }

        Credentials stsCredentials = Credentials.builder()
            .accessKeyId(StringUtils.hasText(credentials.getAccessKey()) ? credentials.getAccessKey() : null)
            .secretAccessKey(StringUtils.hasText(credentials.getSecretKey()) ? credentials.getSecretKey() : null)
            .sessionToken(StringUtils.hasText(credentials.getSessionToken()) ? credentials.getSessionToken() : null)
            .expiration(credentials.getExpiration() != null ? credentials.getExpiration().toInstant() : null)
            .build();

        AssumeRoleWithWebIdentityResponse response = AssumeRoleWithWebIdentityResponse.builder()
            .credentials(stsCredentials)
            .assumedRoleUser(assumedRoleUser(roleArn, roleSessionName))
            .build();

        return toXml(response, userAuthentication.getName());
    }

    private AssumedRoleUser assumedRoleUser(String roleArn, String roleSessionName) {
        if (!StringUtils.hasText(roleArn)) {
            return null;
        }

        String sessionName = StringUtils.hasText(roleSessionName) ? roleSessionName : DEFAULT_SESSION_NAME;

        String[] parts = roleArn.split(":", 6);
        String account = parts.length > 4 ? parts[4] : "";
        String resource = parts.length > 5 ? parts[5] : roleArn;
        String roleName = resource.substring(resource.lastIndexOf('/') + 1);

        return AssumedRoleUser.builder()
            .arn("arn:aws:sts::" + account + ":assumed-role/" + roleName + "/" + sessionName)
            .assumedRoleId(roleName + ":" + sessionName)
            .build();
    }

    //the sts query protocol requires an xml response, build it explicitly
    private String toXml(AssumeRoleWithWebIdentityResponse response, String subject) {
        Credentials credentials = response.credentials();
        StringBuilder sb = new StringBuilder();
        sb.append("<AssumeRoleWithWebIdentityResponse xmlns=\"").append(XMLNS).append("\">");
        sb.append("<AssumeRoleWithWebIdentityResult>");
        if (StringUtils.hasText(subject)) {
            sb.append("<SubjectFromWebIdentityToken>").append(escape(subject)).append("</SubjectFromWebIdentityToken>");
        }
        if (credentials != null) {
            sb.append("<Credentials>");
            if (credentials.accessKeyId() != null) {
                sb.append("<AccessKeyId>").append(escape(credentials.accessKeyId())).append("</AccessKeyId>");
            }
            if (credentials.secretAccessKey() != null) {
                sb
                    .append("<SecretAccessKey>")
                    .append(escape(credentials.secretAccessKey()))
                    .append("</SecretAccessKey>");
            }
            if (credentials.sessionToken() != null) {
                sb.append("<SessionToken>").append(escape(credentials.sessionToken())).append("</SessionToken>");
            }
            if (credentials.expiration() != null) {
                sb
                    .append("<Expiration>")
                    .append(
                        DateTimeFormatter.ISO_INSTANT.format(credentials.expiration().truncatedTo(ChronoUnit.SECONDS))
                    )
                    .append("</Expiration>");
            }
            sb.append("</Credentials>");
        }
        AssumedRoleUser assumedRoleUser = response.assumedRoleUser();
        if (assumedRoleUser != null) {
            sb
                .append("<AssumedRoleUser>")
                .append("<Arn>")
                .append(escape(assumedRoleUser.arn()))
                .append("</Arn>")
                .append("<AssumedRoleId>")
                .append(escape(assumedRoleUser.assumedRoleId()))
                .append("</AssumedRoleId>")
                .append("</AssumedRoleUser>");
        }
        sb.append("</AssumeRoleWithWebIdentityResult>");
        sb
            .append("<ResponseMetadata><RequestId>")
            .append(UUID.randomUUID().toString())
            .append("</RequestId></ResponseMetadata>");
        sb.append("</AssumeRoleWithWebIdentityResponse>");

        return sb.toString();
    }

    private String escape(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    //aws clients expect errors as xml in the query protocol envelope, not as json

    @ExceptionHandler(InvalidActionException.class)
    public ResponseEntity<String> handleInvalidAction(InvalidActionException e) {
        return error(HttpStatus.BAD_REQUEST, "Sender", "InvalidAction", e.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> handleAuthenticationException(AuthenticationException e) {
        return error(HttpStatus.FORBIDDEN, "Sender", "InvalidIdentityToken", e.getMessage());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<String> handleUnsupportedOperation(UnsupportedOperationException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Receiver", "ServiceUnavailable", e.getMessage());
    }

    private ResponseEntity<String> error(HttpStatus status, String type, String code, String message) {
        String xml =
            "<ErrorResponse xmlns=\"" +
            XMLNS +
            "\"><Error><Type>" +
            type +
            "</Type><Code>" +
            code +
            "</Code><Message>" +
            escape(message != null ? message : code) +
            "</Message></Error><RequestId>" +
            UUID.randomUUID() +
            "</RequestId></ErrorResponse>";

        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_XML).body(xml);
    }

    static class InvalidActionException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        InvalidActionException(String action) {
            super("Could not find operation " + action + " for version " + VERSION);
        }
    }
}
