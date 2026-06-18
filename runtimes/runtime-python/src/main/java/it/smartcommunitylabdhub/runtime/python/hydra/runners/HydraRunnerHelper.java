package it.smartcommunitylabdhub.runtime.python.hydra.runners;

import java.util.Collections;
import java.util.List;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.runtime.python.hydra.model.HydraConfig;

public class HydraRunnerHelper {


    public static List<ContextSource> createConfigSources(HydraConfig config) {
        if (config != null) {
            // force using inline config at runtime dir
            String path = "config.yaml";
            if (StringUtils.hasText(config.getBase64())) {
                return Collections.singletonList(ContextSource.builder().name(path).base64(config.getBase64()).build());
            }
        }
        return List.of();
    }

    public static List<ContextRef> createConfigRefs(HydraConfig config) {
        if (config != null && StringUtils.hasText(config.getSource())) {
            try {
                //evaluate if local path (no scheme)
                UriComponents uri = UriComponentsBuilder.fromUriString(config.getSource()).build();
                String scheme = uri.getScheme();

                if (scheme != null) {
                    //write as ref
                    return Collections.singletonList(ContextRef.from(config.getSource()));
                }
            } catch (IllegalArgumentException e) {
                //skip invalid source
            }
        }

        return List.of();
    }
}
