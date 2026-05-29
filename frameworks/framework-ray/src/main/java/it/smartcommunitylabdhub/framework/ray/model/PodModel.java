package it.smartcommunitylabdhub.framework.ray.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import it.smartcommunitylabdhub.framework.k8s.objects.CoreAffinity;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreImagePullPolicy;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreNodeSelector;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResources;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreToleration;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class PodModel {

    private String image;

    private CoreResources resources;
    private List<CoreVolume> volumes;

    @JsonProperty("node_selector")
    private List<CoreNodeSelector> nodeSelector;

    private CoreAffinity affinity;

    private List<CoreToleration> tolerations;

    private String runtimeClass;

    private String priorityClass;

    private CoreImagePullPolicy imagePullPolicy;

    //securityContext
    private Integer runAsUser;
    private Integer runAsGroup;
    private Integer fsGroup;

    private List<CoreLabel> labels;

    private String template;

    private Map<String, String> rayResources;
    private Map<String, String> startParams;

    private String[] command;

    private String[] args;


    /**
     * Convert this PodModel to a K8sRunnable using the provided builder and parent runnable.
      * The name parameter is used to differentiate between different pods (e.g., head, worker) when constructing the runnable.
      * From the parent runnable, it will inherit common properties such as project, runtime, user, secrets, envs, task, configurationMap, credentialsMap.
     * @param <T>
     * @param parent
     * @param name
     * @param builder
     * @param withContext whether to include contextRefs and contextSources from the parent runnable; should be false for worker runnables as they won't have access to the same contexts as the head
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T extends K8sRunnable> T toK8sRunnable(T parent, String name, K8sRunnable.K8sRunnableBuilder<?, ?> builder, boolean withContext) {
        
        return (T) builder
        // .id(parent.getId() + "-" + name)
        .id(parent.getId())
        .configurationMap(parent.getConfigurationMap())
        .credentialsMap(parent.getCredentialsMap())
        .project(parent.getProject())
        .runtime(parent.getRuntime())
        .user(parent.getUser())
        .secrets(parent.getSecrets())
        .envs(parent.getEnvs())
        .task(parent.getTask())
        .template(template)
        .command(command)
        .args(args)
        .image(image)
        .resources(resources)
        .volumes(volumes)
        .nodeSelector(nodeSelector)
        .affinity(affinity)
        .tolerations(tolerations)
        .runtimeClass(runtimeClass)
        .priorityClass(priorityClass)
        .imagePullPolicy(imagePullPolicy)
        .runAsUser(runAsUser)
        .runAsGroup(runAsGroup)
        .fsGroup(fsGroup)
        .labels(labels)
        .contextRefs(withContext ? parent.getContextRefs() : null)
        .contextSources(withContext ? parent.getContextSources() : null)
        .build();
    }
}
