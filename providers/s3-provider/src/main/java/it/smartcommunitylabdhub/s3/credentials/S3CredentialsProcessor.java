package it.smartcommunitylabdhub.s3.credentials;

import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.s3.config.S3Properties;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@EffectType(stages = { "onReady" }, type = Run.class)
@Component
@ConditionalOnBean(S3AssumeRoleProvider.class)
@Slf4j
public class S3CredentialsProcessor implements Effect<Run> {

    public static final String AWS_WEB_IDENTITY_TOKEN_FILE = "aws-web-identity-token-file";

    @Autowired
    private S3Properties properties;

    @Override
    public K8sRunnable process(String stage, Run run, Serializable input) throws CoreRuntimeException {
        if (run == null || input == null) {
            return null;
        }

        if (input instanceof K8sRunnable runnable && runnable.getCredentials() != null) {
            //check if credentials for S3 and STS are present
            S3StaticCredentials s3creds = runnable
                .getCredentials()
                .stream()
                .filter(c -> c instanceof S3StaticCredentials)
                .map(c -> (S3StaticCredentials) c)
                .findFirst()
                .orElse(null);
            S3WebIdentityCredentials s3webcreds = runnable
                .getCredentials()
                .stream()
                .filter(c -> c instanceof S3WebIdentityCredentials)
                .map(c -> (S3WebIdentityCredentials) c)
                .findFirst()
                .orElse(null);

            if (
                properties.isWebIdentityProviderEnabled() &&
                s3webcreds != null &&
                s3webcreds.getWebIdentityTokenFile() == null &&
                s3webcreds.getWebIdentityToken() != null
            ) {
                log.debug("process web identity credentials into file...");
                //web identity credentials have priority, clear s3creds and write into file
                if (s3creds != null) {
                    s3creds.eraseCredentials();
                }

                String mountPath = "/." + AWS_WEB_IDENTITY_TOKEN_FILE;
                s3webcreds.setWebIdentityTokenFile(mountPath);

                if (log.isTraceEnabled()) {
                    log.trace("web identity token file will be set to: {}", mountPath);
                }

                ContextSource ref = ContextSource.builder()
                    .name(AWS_WEB_IDENTITY_TOKEN_FILE)
                    .base64(
                        Base64.getEncoder()
                            .withoutPadding()
                            .encodeToString(s3webcreds.getWebIdentityToken().getBytes(StandardCharsets.UTF_8))
                    )
                    .mountPath(mountPath)
                    .build();

                ArrayList<ContextSource> contextSources =
                    runnable.getContextSources() != null
                        ? new ArrayList<>(runnable.getContextSources())
                        : new ArrayList<>();
                contextSources.add(ref);
                runnable.setContextSources(contextSources);
            }

            return runnable;
        }

        return null;
    }
}
