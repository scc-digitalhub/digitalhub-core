/*
 * SPDX-FileCopyrightText: © 2026 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.s3.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.AuthorizableAwareEntityService;
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.s3.config.S3Properties;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Integration test for {@link S3AssumeRoleProvider}.
 *
 * <p>This test exercises the provider against a real STS-compatible endpoint. It is gated on the
 * presence of {@code S3_IT_ENDPOINT} in the environment so the build does not fail when no
 * credentials are available. To run it, export the following variables (only the first three are
 * mandatory):</p>
 *
 * <pre>
 *   S3_IT_ENDPOINT=https://sts.example.org      # STS-compatible endpoint
 *   S3_IT_ACCESS_KEY=AKIA...                    # long-lived credentials with sts:AssumeRole
 *   S3_IT_SECRET_KEY=...                        # corresponding secret key
 *   S3_IT_ROLE_ARN=arn:aws:iam::123:role/foo    # role to assume (optional on some impls, e.g. MinIO)
 *   S3_IT_REGION=us-east-1                      # region (default us-east-1)
 *   S3_IT_BUCKET=my-bucket                      # bucket to list against (optional, enables S3 round-trip)
 *   S3_IT_S3_ENDPOINT=https://s3.example.org    # S3 endpoint (defaults to S3_IT_ENDPOINT)
 *   S3_IT_POLICY={...inline session policy...}  # optional inline session policy
 * </pre>
 *
 * <p>Run with:</p>
 * <pre>
 *   ./mvnw test -pl providers/s3-provider \
 *     -Dtest=S3AssumeRoleProviderIntegrationTest
 * </pre>
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "S3_IT_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "S3_IT_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "S3_IT_SECRET_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "S3_IT_ROLE_ARN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "S3_IT_PROJECTS", matches = ".+")
@EnabledIfEnvironmentVariable(named = "S3_IT_BUCKET", matches = ".+")
@DisplayName("S3AssumeRoleProvider integration test (real STS endpoint)")
class S3AssumeRoleProviderIntegrationTest {

    // ---- env keys ----
    private static final String ENV_ENDPOINT = "S3_IT_ENDPOINT";
    private static final String ENV_ACCESS_KEY = "S3_IT_ACCESS_KEY";
    private static final String ENV_SECRET_KEY = "S3_IT_SECRET_KEY";
    private static final String ENV_ROLE_ARN = "S3_IT_ROLE_ARN";
    private static final String ENV_REGION = "S3_IT_REGION";
    private static final String ENV_BUCKET = "S3_IT_BUCKET";
    private static final String ENV_S3_ENDPOINT = "S3_IT_S3_ENDPOINT";
    private static final String ENV_POLICY = "S3_IT_POLICY";
    private static final String ENV_PROJECTS = "S3_IT_PROJECTS";

    private static final String CLAIM = "s3";
    private static final String TEST_USER = "it-user";

    private static String endpoint;
    private static String accessKey;
    private static String secretKey;
    private static String roleArn;
    private static String region;
    private static String bucket;
    private static String s3Endpoint;
    private static String policy;

    private S3AssumeRoleProvider provider;

    @BeforeAll
    static void loadEnv() {
        
        endpoint = System.getenv(ENV_ENDPOINT);
        accessKey = System.getenv(ENV_ACCESS_KEY);
        secretKey = System.getenv(ENV_SECRET_KEY);
        roleArn = System.getenv(ENV_ROLE_ARN);
        region = orDefault(System.getenv(ENV_REGION), "us-east-1");
        bucket = System.getenv(ENV_BUCKET);
        s3Endpoint = orDefault(System.getenv(ENV_S3_ENDPOINT), endpoint);
        policy = System.getenv(ENV_POLICY);

        log.info(
            "S3 IT config: endpoint={}, region={}, roleArn={}, bucket={}, hasPolicy={}",
            endpoint,
            region,
            roleArn,
            bucket,
            policy != null
        );
    }

    @BeforeEach
    void setUp() throws Exception {
        provider = newProvider();
    }

    private static S3AssumeRoleProvider newProvider() throws Exception {
        S3Properties props = new S3Properties();
        props.setEnable(Boolean.TRUE);
        props.setEndpoint(endpoint);
        props.setRegion(region);
        props.setBucket(bucket);
        props.setAccessKey(accessKey);
        props.setSecretKey(secretKey);
        props.setClaim(CLAIM);
        props.setRoleArn(roleArn);
        props.setPolicy(policy);
        props.setPathStyleAccess(Boolean.TRUE);

        // sanity: provider config must consider AssumeRole enabled
        if (!props.isAssumeRoleProviderEnabled()) {
            throw new IllegalStateException(
                "AssumeRole provider not enabled with current env; " +
                "ensure at least one of S3_IT_ROLE_ARN or S3_IT_POLICY is set"
            );
        }

        S3AssumeRoleProvider p = new S3AssumeRoleProvider(props);
        // shorter access token duration than default to align with min STS duration (900s on AWS).
        // The provider will up-adjust internal duration accordingly.
        p.setAccessTokenDuration(JwtTokenService.DEFAULT_ACCESS_TOKEN_DURATION);
        p.afterPropertiesSet();
        return p;
    }

    private static UserAuthentication<TestingAuthenticationToken> auth(String username, S3PolicyMapping mapping) {
        TestingAuthenticationToken inner = new TestingAuthenticationToken(
            username,
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        inner.setAuthenticated(true);

        UserAuthentication<TestingAuthenticationToken> u = new UserAuthentication<>(
            inner,
            username,
            inner.getAuthorities()
        );
        u.setCredentials(mapping == null ? Collections.emptyList() : List.of((Credentials) mapping));
        return u;
    }

    private static S3PolicyMapping mapping() {
        return S3PolicyMapping.builder().claim(CLAIM).roleArn(roleArn).policy(policy).build();
    }

    // ---------------------------------------------------------------------
    // tests
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("AssumeRole returns valid temporary credentials")
    void assumeRole_returnsValidCredentials() {
        UserAuthentication<?> a = auth(TEST_USER, mapping());

        Credentials c = provider.get(a);

        assertThat(c).as("credentials must not be null").isNotNull().isInstanceOf(S3Credentials.class);
        S3Credentials s3 = (S3Credentials) c;

        assertThat(s3.getAccessKey()).as("access key").isNotBlank();
        assertThat(s3.getSecretKey()).as("secret key").isNotBlank();
        assertThat(s3.getSessionToken()).as("session token").isNotBlank();
        assertThat(s3.getExpiration())
            .as("expiration must be in the future")
            .isNotNull()
            .isAfter(ZonedDateTime.now());
    }

    @Test
    @DisplayName("Same user + same policy hits cache and returns identical credentials")
    void cache_returnsSameInstanceOnRepeatedCall() {
        UserAuthentication<?> a1 = auth(TEST_USER, mapping());
        UserAuthentication<?> a2 = auth(TEST_USER, mapping());

        S3Credentials first = (S3Credentials) provider.get(a1);
        S3Credentials second = (S3Credentials) provider.get(a2);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.getAccessKey()).isEqualTo(first.getAccessKey());
        assertThat(second.getSessionToken()).isEqualTo(first.getSessionToken());
        assertThat(second.getExpiration()).isEqualTo(first.getExpiration());
    }

    @Test
    @DisplayName("Different users get distinct credential sessions")
    void differentUsers_getDistinctCredentials() {
        S3Credentials a = (S3Credentials) provider.get(auth("user-a", mapping()));
        S3Credentials b = (S3Credentials) provider.get(auth("user-b", mapping()));

        assertThat(a).isNotNull();
        assertThat(b).isNotNull();
        // Provider always issues a new STS call per (user, policy) cache key. The session token
        // (and typically the access key) must differ.
        assertThat(b.getSessionToken()).isNotEqualTo(a.getSessionToken());
    }

    @Test
    @DisplayName("Missing policy mapping in auth context yields no credentials")
    void noPolicyMapping_returnsNull() {
        UserAuthentication<?> a = auth(TEST_USER, null);
        Credentials c = provider.get(a);
        assertThat(c).isNull();
    }

    @Test
    @DisplayName("Returned STS credentials work against the S3 endpoint (when bucket is configured)")
    @EnabledIfEnvironmentVariable(named = ENV_BUCKET, matches = ".+")
    void returnedCredentials_canListBucket() {
        S3Credentials s3 = (S3Credentials) provider.get(auth(TEST_USER, mapping()));
        assertThat(s3).isNotNull();

        AwsSessionCredentials creds = AwsSessionCredentials.create(
            s3.getAccessKey(),
            s3.getSecretKey(),
            s3.getSessionToken()
        );

        try (
            S3Client client = S3Client
                .builder()
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .region(Region.of(region))
                .endpointOverride(URI.create(s3Endpoint))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()
        ) {
            ListObjectsV2Response resp = client.listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucket).maxKeys(1).build()
            );
            //we don't care about the contents - just that the call succeeds with the temp creds
            assertThat(resp).isNotNull();
            log.info("listObjectsV2 on bucket {} returned {} object(s)", bucket, resp.keyCount());
        }
    }

    @Test
    @DisplayName("Bogus role ARN produces a StoreException-wrapped STS error")
    @EnabledIfEnvironmentVariable(named = ENV_ROLE_ARN, matches = ".+")
    void bogusRole_failsFast() throws Exception {
        S3Properties props = new S3Properties();
        props.setEnable(Boolean.TRUE);
        props.setEndpoint(endpoint);
        props.setRegion(region);
        props.setAccessKey(accessKey);
        props.setSecretKey(secretKey);
        props.setClaim(CLAIM);
        props.setRoleArn("arn:aws:iam::000000000000:role/this-role-does-not-exist");
        props.setPathStyleAccess(Boolean.TRUE);

        S3AssumeRoleProvider bogus = new S3AssumeRoleProvider(props);
        bogus.setAccessTokenDuration(JwtTokenService.DEFAULT_ACCESS_TOKEN_DURATION);
        bogus.afterPropertiesSet();

        S3PolicyMapping bogusMapping = S3PolicyMapping
            .builder()
            .claim(CLAIM)
            .roleArn("arn:aws:iam::000000000000:role/this-role-does-not-exist")
            .build();

        UserAuthentication<?> a = auth(TEST_USER, bogusMapping);

        // get() swallows ExecutionException and logs; result must be null on STS failure
        Credentials c = bogus.get(a);
        assertThat(c).as("invalid role must not yield credentials").isNull();
    }

    @Test
    @DisplayName("Policy template is resolved with project names from the (mocked) projectAuthHelper")
    @EnabledIfEnvironmentVariable(named = ENV_ROLE_ARN, matches = ".+")
    void policyTemplate_resolvesProjectsFromAuthHelper() throws Exception {
        // pick the projects to grant access to; default to two synthetic ids when not provided
        String projectsCsv = orDefault(System.getenv(ENV_PROJECTS), "it-project-a,it-project-b");
        List<String> ownProjects = Arrays
            .stream(projectsCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        String prefix = ownProjects.get(0) + "/"; // we will list objects with this prefix to validate the policy grants access to the right "folder"

        // Read/Write policy template, scoped to a "folder" (key prefix) per project name.
        // The {{#projects}}...{{/projects}} section is filled by the provider from the
        // mocked AuthorizableAwareEntityService below.
        String template = readWriteFolderPolicyTemplate();

        S3Properties props = new S3Properties();
        props.setEnable(Boolean.TRUE);
        props.setEndpoint(endpoint);
        props.setRegion(region);
        props.setBucket(bucket);
        props.setAccessKey(accessKey);
        props.setSecretKey(secretKey);
        props.setClaim(CLAIM);
        props.setRoleArn(roleArn);
        props.setPolicyTemplate(template);
        props.setPathStyleAccess(Boolean.TRUE);

        S3AssumeRoleProvider p = new S3AssumeRoleProvider(props);
        p.setAccessTokenDuration(JwtTokenService.DEFAULT_ACCESS_TOKEN_DURATION);
        // wire the mock helper - same package, can call the package-private setter directly
        p.setProjectAuthHelper(new StaticProjectAuthHelper(TEST_USER, ownProjects, Collections.emptyList()));
        p.afterPropertiesSet();

        // mapping with NO inline policy and NO inline roleArn override - forces the provider to
        // resolve the policy via the configured policyTemplate + projectAuthHelper
        S3PolicyMapping templateMapping = S3PolicyMapping.builder().claim(CLAIM).roleArn(roleArn).build();

        UserAuthentication<?> a = auth(TEST_USER, templateMapping);

        Credentials c = p.get(a);

        assertThat(c)
            .as("credentials must be issued when policy template resolves to a non-empty session policy")
            .isNotNull()
            .isInstanceOf(S3Credentials.class);

        S3Credentials s3 = (S3Credentials) c;
        assertThat(s3.getAccessKey()).isNotBlank();
        assertThat(s3.getSecretKey()).isNotBlank();
        assertThat(s3.getSessionToken()).isNotBlank();
        assertThat(s3.getExpiration()).isAfter(ZonedDateTime.now());

        // mapping policy must have been restored after lookup so the cache key is stable
        assertThat(templateMapping.getPolicy())
            .as("provider must restore the original (empty) policy on the mapping after cache lookup")
            .isNullOrEmpty();

        AwsSessionCredentials creds = AwsSessionCredentials.create(
            s3.getAccessKey(),
            s3.getSecretKey(),
            s3.getSessionToken()
        );

        // log.info("credentials can list bucket {}: {} object(s)", bucket, );
        assertThat(listObjects(bucket, prefix, creds).size())
            .as("credentials from resolved policy template must grant access to the project folder in S3")
            .isNotEqualTo(0);

        // sanity: an unknown user should resolve to an empty project set; with a Deny-everything-
        // else statement and no Allow targets this typically still produces a (degenerate) policy
        // that STS will accept - we just assert the call path doesn't blow up.
        S3PolicyMapping otherMapping = S3PolicyMapping.builder().claim(CLAIM).roleArn(roleArn).build();
        UserAuthentication<?> other = auth("unknown-user", otherMapping);
        try {
            Credentials oc = p.get(other);
            // either credentials are returned (STS accepts the deny-only policy) or null when
            // the impl rejects it - both are acceptable; the important bit is no exception bubbles up
            log.info("unknown-user template resolution -> {}", oc != null ? "credentials issued" : "no credentials");
            // use credentials to list the objects in the bucket, if returned - this validates that the policy is well-formed even in the degenerate case of no projects
            if (oc != null) {
                S3Credentials os3 = (S3Credentials) oc;
                AwsSessionCredentials ocreds = AwsSessionCredentials.create(
                    os3.getAccessKey(),
                    os3.getSecretKey(),
                    os3.getSessionToken()
                );

               // list objects should fail with access denied
                assertThrows(S3Exception.class, () -> listObjects(bucket, prefix, ocreds).size());
            }
        } catch (RuntimeException re) {
            // ExecutionException from cache.get is swallowed inside the provider; any other
            // RuntimeException would indicate a regression in the resolution path
            throw new AssertionError("template resolution path must not throw for unknown user", re);
        }
    }

    private List<S3Object> listObjects(String bucket, String prefix, AwsSessionCredentials creds) {
        S3Client s3c = S3Client
            .builder()
            .credentialsProvider(StaticCredentialsProvider.create(creds))
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
        List<S3Object> objects = s3c.listObjects(ListObjectsRequest
            .builder()
            .bucket(bucket)
            .prefix(prefix)
            .build()).contents();
        return objects;
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * Renders a read/write S3 session policy that grants access to a list of "folders" (key
     * prefixes), one per project name. The {@code projects}/{@code ownProjects}/{@code
     * sharedProjects} variables are injected by
     * {@link S3AssumeRoleProvider#resolvePolicyTemplate(String, UserAuthentication)} from the
     * mocked {@link AuthorizableAwareEntityService}; {@code bucket} is baked in at template
     * compile time so the provider doesn't need to know about it.
     */
    private static String readWriteFolderPolicyTemplate() {
        return (
            // SEE https://aws.amazon.com/blogs/security/writing-iam-policies-grant-access-to-user-specific-folders-in-an-amazon-s3-bucket/
            "{\n" +
            "  \"Version\": \"2012-10-17\",\n" +
            "  \"Statement\": [\n" +
            "    {\n" + //
            "       \"Sid\": \"AllowListingOfProjectFolder\",\n" + //
            "       \"Action\": [\"s3:ListBucket\"],\n" + //
            "       \"Effect\": \"Allow\",\n" + //
            "       \"Resource\": [\"arn:aws:s3:::{{bucket}}\"],\n" + //
            "       \"Condition\":{\"StringLike\":{\"s3:prefix\":[{{#projects}}\"{{.}}/*\", {{/projects}}\"__none__/*\"]}}\n" + //
            "    },\n" +
            "    {\n" +
            "      \"Sid\": \"ReadWriteProjectFolders\",\n" +
            "      \"Effect\": \"Allow\",\n" +
            "      \"Action\": [\"s3:*\"],\n" +
            "      \"Resource\": [\n" +
            "        {{#projects}}\"arn:aws:s3:::{{bucket}}/{{.}}/*\",\n" +
            "        {{/projects}}\"arn:aws:s3:::{{bucket}}/__none__/*\"\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}"
        );
    }

    /**
     * Hand-rolled stub for {@link AuthorizableAwareEntityService} that returns a fixed list of
     * project ids for a given user as "owned" projects. Used to validate the policy-template
     * resolution path of {@link S3AssumeRoleProvider} without pulling in Mockito.
     */
    private static final class StaticProjectAuthHelper implements AuthorizableAwareEntityService<Project> {

        private final String user;
        private final List<String> ownIds;
        private final List<String> sharedIds;

        StaticProjectAuthHelper(String user, List<String> ownIds, List<String> sharedIds) {
            this.user = user;
            this.ownIds = ownIds;
            this.sharedIds = sharedIds;
        }

        @Override
        public List<String> findIdsByCreatedBy(@NotNull String createdBy) {
            return user.equals(createdBy) ? ownIds : Collections.emptyList();
        }

        @Override
        public List<String> findIdsByUpdatedBy(@NotNull String updatedBy) {
            return Collections.emptyList();
        }

        @Override
        public List<String> findIdsByProject(@NotNull String project) {
            return Collections.emptyList();
        }

        @Override
        public List<String> findIdsBySharedTo(@NotNull String shared) {
            return user.equals(shared) ? sharedIds : Collections.emptyList();
        }

        @Override
        public List<String> findNamesByCreatedBy(@NotNull String createdBy) {
            return findIdsByCreatedBy(createdBy);
        }

        @Override
        public List<String> findNamesByUpdatedBy(@NotNull String updatedBy) {
            return Collections.emptyList();
        }

        @Override
        public List<String> findNamesByProject(@NotNull String project) {
            return Collections.emptyList();
        }

        @Override
        public List<String> findNamesBySharedTo(@NotNull String shared) {
            return findIdsBySharedTo(shared);
        }
    }
}
