package it.smartcommunitylabdhub.framework.k8s.runnables;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.SecuredRunnable;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreAffinity;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLog;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreMetric;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreNodeSelector;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreToleration;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.CredentialsContainer;

@SuperBuilder(toBuilder = true)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class K8sRunnable implements RunRunnable, SecuredRunnable, CredentialsContainer {

    private String id;

    private String project;

    private String runtime;

    private String task;

    private String image;

    private String command;

    private String[] args;

    private List<CoreEnv> envs;

    private List<CoreEnv> secrets;

    private CoreResource resources;

    private List<CoreVolume> volumes;

    @JsonProperty("node_selector")
    private List<CoreNodeSelector> nodeSelector;

    private CoreAffinity affinity;

    private List<CoreToleration> tolerations;

    private String runtimeClass;

    private String priorityClass;

    //securityContext
    private Integer runAsUser;
    private Integer runAsGroup;
    private Integer fsGroup;

    private List<CoreLabel> labels;

    private String template;

    private String state;

    private String error;

    private String message;

    private Map<String, Serializable> results;

    @JsonIgnore
    @ToString.Exclude
    private List<CoreLog> logs;

    @JsonIgnore
    @ToString.Exclude
    private List<CoreMetric> metrics;

    @ToString.Exclude
    private HashMap<String, String> credentials;

    @JsonProperty("context_refs")
    private List<ContextRef> contextRefs;

    @JsonProperty("context_sources")
    private List<ContextSource> contextSources;

    @Override
    public String getFramework() {
        return "k8s";
    }

    @Override
    public void eraseCredentials() {
        this.credentials = null;
    }

    @Override
    public void setCredentials(Serializable credentials) {
        if (credentials != null) {
            //try to coerce into map
            HashMap<String, Object> map = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
                credentials,
                JacksonMapper.typeRef
            );

            this.credentials =
                map
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue() != null)
                    .map(e -> {
                        if (e.getValue() instanceof String) {
                            return Map.entry(e.getKey(), (String) e.getValue());
                        }

                        try {
                            String value = JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(e.getValue());
                            return Map.entry(e.getKey(), value);
                        } catch (JsonProcessingException je) {
                            return null;
                        }
                    })
                    .filter(e -> e.getValue() != null)
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (o1, o2) -> o1, HashMap::new));
        }
    }
}
