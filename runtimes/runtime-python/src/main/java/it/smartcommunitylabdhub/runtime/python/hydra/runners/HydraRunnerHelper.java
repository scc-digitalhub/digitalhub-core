package it.smartcommunitylabdhub.runtime.python.hydra.runners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.runs.Run;
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
                    ContextRef ref = ContextRef.from(config.getSource());
                    ref.setDestination("config/");
                    return Collections.singletonList(ref);
                }
            } catch (IllegalArgumentException e) {
                //skip invalid source
            }
        }

        return List.of();
    }

    /**
     * Create shared dir volume as shared dir
     * @param run
     * @param taskSpec
     * @param volumeSizeSpec
     * @param k8sBuilderHelper
     * @return
     */
    protected static List<CoreVolume> createVolumes(Run run, K8sFunctionTaskBaseSpec taskSpec, String volumeSizeSpec, K8sBuilderHelper k8sBuilderHelper, String id, CoreVolume.VolumeType volumeType) {
        List<CoreVolume> coreVolumes = new ArrayList<>(
            taskSpec.getVolumes() != null ? taskSpec.getVolumes() : List.of()
        );
        //check if scratch disk is requested as resource or set default
        String volumeSize = taskSpec.getResources() != null && taskSpec.getResources().getDisk() != null
            ? taskSpec.getResources().getDisk()
            : volumeSizeSpec;
        CoreResource diskResource = new CoreResource();
        diskResource.setDisk(volumeSize);

        Optional
            .ofNullable(k8sBuilderHelper)
            .ifPresent(helper -> {
                Optional.ofNullable(buildSharedSharedVolume(diskResource, "pvc-hydra-" + id, volumeType)).ifPresent(coreVolumes::add);
            });

        return coreVolumes;
    }

    public static CoreVolume buildSharedSharedVolume(CoreResource resource, String name, CoreVolume.VolumeType volumeType) {
        if (resource != null && resource.getDisk() != null) {
            Map<String, String> volumeOptions = new HashMap<>();
            volumeOptions.put("size", resource.getDisk());
            volumeOptions.put("claimName", name);
            //add shared volume for scratch disk as /shared
            return new CoreVolume(
                volumeType,
                "/shared",
                "shared-dir",
                volumeOptions
            );
        }

        return null;
    }
}
