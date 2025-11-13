/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.argo.infrastructure.k8s;

import com.fasterxml.jackson.core.type.TypeReference;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Arguments;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1ArtifactRepositoryRef;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1ExecutorConfig;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Parameter;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Template;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Workflow;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowSpec;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.jackson.YamlMapperFactory;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.framework.argo.exceptions.K8sArgoFrameworkException;
import it.smartcommunitylabdhub.framework.argo.objects.K8sWorkflowObject;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoWorkflowRunnable;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sBaseFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@FrameworkComponent(framework = K8sArgoWorkflowFramework.FRAMEWORK)
public class K8sArgoWorkflowFramework extends K8sBaseFramework<K8sArgoWorkflowRunnable, K8sWorkflowObject> {

    private static final String ARGO_PLURAL = "workflows";
    private static final String ARGO_VERSION = "v1alpha1";
    private static final String ARGO_GROUP = "argoproj.io";
    private static final String ARGO_API_VERSION = "argoproj.io/v1alpha1";

    public static final int DEFAULT_BACKOFF_LIMIT = 3;
    public static final String FRAMEWORK = "argoworkflow";

    private final CustomObjectsApi customObjectsApi;

    private String artifactRepositoryConfigMap;
    private String artifactRepositoryKey;
    private String serviceAccountName;
    private Integer runAsUser;
    protected String pvcStorageClass;
    protected String pvcAccessMode = "ReadWriteMany";

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    public K8sArgoWorkflowFramework(ApiClient apiClient) {
        super(apiClient);
        customObjectsApi = new CustomObjectsApi(apiClient);
    }

    @Autowired
    public void setArtifactRepositoryConfigMap(@Value("${argoworkflows.artifacts.configmap}") String configmap) {
        this.artifactRepositoryConfigMap = configmap;
    }

    @Autowired
    public void setArtifactRepositoryKey(@Value("${argoworkflows.artifacts.key}") String key) {
        this.artifactRepositoryKey = key;
    }

    @Autowired
    public void setServiceAccountName(@Value("${argoworkflows.serviceaccount}") String serviceAccountName) {
        this.serviceAccountName = serviceAccountName;
    }

    @Autowired
    public void setRunAsUser(@Value("${argoworkflows.user}") Integer runAsUser) {
        this.runAsUser = runAsUser;
    }

    @Autowired
    @Override
    public void setPvcStorageClass(@Value("${kubernetes.resources.workflow.storage-class}") String pvcStorageClass) {
        if (StringUtils.hasText(pvcStorageClass)) {
            this.pvcStorageClass = pvcStorageClass;
        }
    }

    @Autowired
    public void setPvcAccessMode(@Value("${kubernetes.resources.workflow.access-mode}") String pvcAccessMode) {
        this.pvcAccessMode = pvcAccessMode;
    }

    @Override
    public K8sArgoWorkflowRunnable run(K8sArgoWorkflowRunnable runnable) throws K8sFrameworkException {
        log.info("run for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        Map<String, KubernetesObject> results = new HashMap<>();

        //secrets
        V1Secret secret = buildRunSecret(runnable);
        if (secret != null) {
            storeRunSecret(secret);
            //clear data before storing
            results.put("secret", secret.stringData(Collections.emptyMap()).data(Collections.emptyMap()));
        }

        String name = null;
        if (runnable.getWorkflowSpec() == null) {
            throw new K8sArgoFrameworkException("Missing workflow specification");
        }

        if (runnable.getWorkflowSpec() != null) {
            //create workflow with reference to secret
            K8sWorkflowObject workflow = build(runnable);
            log.info("create workflow for {}", String.valueOf(workflow.getMetadata().getName()));

            //pvcs
            List<V1PersistentVolumeClaim> pvcs = buildPersistentVolumeClaims(runnable);
            if (pvcs != null) {
                for (V1PersistentVolumeClaim pvc : pvcs) {
                    log.info("create pvc for {}", String.valueOf(pvc.getMetadata().getName()));
                    try {
                        coreV1Api.createNamespacedPersistentVolumeClaim(namespace, pvc, null, null, null, null);
                        //store
                        results.put("pvc", pvc);
                    } catch (ApiException e) {
                        log.error("Error with k8s: {}", e.getMessage());
                        if (log.isTraceEnabled()) {
                            log.trace("k8s api response: {}", e.getResponseBody());
                        }

                        throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
                    }
                }
            }

            workflow = create(workflow);
            results.put("workflow", workflow);
            name = workflow.getMetadata().getName();
        }

        //update state
        runnable.setState(K8sRunnableState.RUNNING.name());

        //update results
        if (!"disable".equals(collectResults)) {
            //update results
            try {
                runnable.setResults(
                    results
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(Entry::getKey, e -> mapper.convertValue(e, typeRef)))
                );
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s results: {}", e.getMessage());
            }
        }

        if (name != null) {
            runnable.setMessage(String.format("argo workflow %s created", name));
        }

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public K8sArgoWorkflowRunnable stop(K8sArgoWorkflowRunnable runnable) throws K8sFrameworkException {
        log.info("stop for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        List<String> messages = new ArrayList<>();

        K8sWorkflowObject workflow = get(build(runnable));

        //stop by deleting
        log.info("stopping Argo Workflow for {}", String.valueOf(workflow.getMetadata().getName()));
        delete(workflow);
        messages.add(String.format("workflow %s deleted", workflow.getMetadata().getName()));

        //pvcs
        List<V1PersistentVolumeClaim> pvcs = buildPersistentVolumeClaims(runnable);
        if (pvcs != null) {
            for (V1PersistentVolumeClaim pvc : pvcs) {
                String pvcName = pvc.getMetadata().getName();
                try {
                    V1PersistentVolumeClaim v = coreV1Api.readNamespacedPersistentVolumeClaim(pvcName, namespace, null);
                    if (v != null) {
                        log.info("delete pvc for {}", String.valueOf(pvcName));

                        coreV1Api.deleteNamespacedPersistentVolumeClaim(
                            pvcName,
                            namespace,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "Background",
                            null
                        );
                        messages.add(String.format("pvc %s deleted", pvcName));
                    }
                } catch (ApiException e) {
                    log.error("Error with k8s: {}", e.getMessage());
                    if (log.isTraceEnabled()) {
                        log.trace("k8s api response: {}", e.getResponseBody());
                    }
                    //don't propagate
                    // throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
                }
            }
        }

        //update state
        runnable.setState(K8sRunnableState.STOPPED.name());
        runnable.setMessage(String.join(", ", messages));

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public K8sArgoWorkflowRunnable delete(K8sArgoWorkflowRunnable runnable) throws K8sFrameworkException {
        log.info("delete for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        List<String> messages = new ArrayList<>();

        K8sWorkflowObject workflow;
        try {
            workflow = get(build(runnable));
        } catch (K8sFrameworkException | IllegalArgumentException e) {
            runnable.setState(K8sRunnableState.DELETED.name());
            return runnable;
        }

        //secrets
        cleanRunSecret(runnable);

        log.info("delete Argo Workflow for {}", String.valueOf(workflow.getMetadata().getName()));
        delete(workflow);
        messages.add(String.format("workflow %s deleted", workflow.getMetadata().getName()));

        //pvcs
        List<V1PersistentVolumeClaim> pvcs = buildPersistentVolumeClaims(runnable);
        if (pvcs != null) {
            for (V1PersistentVolumeClaim pvc : pvcs) {
                String pvcName = pvc.getMetadata().getName();
                try {
                    V1PersistentVolumeClaim v = coreV1Api.readNamespacedPersistentVolumeClaim(pvcName, namespace, null);
                    if (v != null) {
                        log.info("delete pvc for {}", String.valueOf(pvcName));

                        coreV1Api.deleteNamespacedPersistentVolumeClaim(
                            pvcName,
                            namespace,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "Background",
                            null
                        );
                        messages.add(String.format("pvc %s deleted", pvcName));
                    }
                } catch (ApiException e) {
                    log.error("Error with k8s: {}", e.getMessage());
                    if (log.isTraceEnabled()) {
                        log.trace("k8s api response: {}", e.getResponseBody());
                    }
                    //don't propagate
                    // throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
                }
            }
        }

        //update state
        runnable.setState(K8sRunnableState.DELETED.name());

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    /**
     * Build Argo Workflow CR. Populate template defaults and artifact repository reference.
     * @param runnable
     * @param secret
     * @return
     * @throws K8sArgoFrameworkException
     */
    public K8sWorkflowObject build(K8sArgoWorkflowRunnable runnable) throws K8sArgoFrameworkException {
        log.debug("build for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        try {
            IoArgoprojWorkflowV1alpha1Workflow workflow = new IoArgoprojWorkflowV1alpha1Workflow();
            workflow.setApiVersion(ARGO_API_VERSION);
            workflow.setKind("Workflow");
            workflow.setMetadata(new V1ObjectMeta());
            workflow.getMetadata().setName(runnable.getId());
            IoArgoprojWorkflowV1alpha1WorkflowSpec workflowSpec = YamlMapperFactory
                .yamlObjectMapper()
                .readValue(runnable.getWorkflowSpec(), IoArgoprojWorkflowV1alpha1WorkflowSpec.class);
            workflow.setSpec(workflowSpec);
            sanitize(workflow);
            //build labels
            Map<String, String> labels = buildLabels(runnable);
            // Create the Job metadata
            workflow.getMetadata().labels(labels);
            //populate template defaults
            appendTemplateDefaults(workflow, runnable);
            //populate artifact repository
            appendArtifactRepository(workflow);
            workflow.getSpec().setSecurityContext(buildPodSecurityContext(runnable));
            // populate inputs
            populateInputs(runnable, workflow);

            return new K8sWorkflowObject(workflow);
        } catch (IOException e) {
            throw new K8sArgoFrameworkException("Error parsing the Argo workflow specification: " + e.getMessage(), e);
        } catch (K8sFrameworkException e) {
            throw new K8sArgoFrameworkException("Error with k8s framework: " + e.getMessage());
        }
    }

    private void populateInputs(K8sArgoWorkflowRunnable runnable, IoArgoprojWorkflowV1alpha1Workflow workflow) {
        //set parameters in workflow if defined in runnable
        if (runnable.getParameters() != null && workflow.getSpec() != null) {
            //args must be set to set parameters
            IoArgoprojWorkflowV1alpha1Arguments arguments = Optional
                .ofNullable(workflow.getSpec().getArguments())
                .orElse(new IoArgoprojWorkflowV1alpha1Arguments());

            if (workflow.getSpec().getArguments() == null) {
                workflow.getSpec().setArguments(new IoArgoprojWorkflowV1alpha1Arguments());
            }

            //collect current params, if preset
            Map<String, IoArgoprojWorkflowV1alpha1Parameter> parameters = Optional
                .ofNullable(arguments.getParameters())
                .map(params -> params.stream().collect(Collectors.toMap(p -> p.getName(), p -> p)))
                .orElse(Collections.emptyMap());

            runnable
                .getParameters()
                .entrySet()
                .stream()
                .forEach(e -> {
                    IoArgoprojWorkflowV1alpha1Parameter p = parameters.get(e.getKey());
                    if (p == null) {
                        p = new IoArgoprojWorkflowV1alpha1Parameter();
                        p.setName(e.getKey());

                        arguments.addParametersItem(p);
                        p.setValue(e.getValue() != null ? e.getValue().toString() : null);
                    } else {
                        p.setValue(e.getValue() != null ? e.getValue().toString() : null);
                    }
                });

            //update args in workflow
            workflow.getSpec().setArguments(arguments);
        }
    }

    private void sanitize(IoArgoprojWorkflowV1alpha1Workflow workflow) {
        // clean up envFrom from template defaults and templates
        // if (workflow.getSpec().getTemplateDefaults() != null) {
        //     workflow.getSpec().getTemplateDefaults().setServiceAccountName(serviceAccountName);
        //     V1Container container = workflow.getSpec().getTemplateDefaults().getContainer();
        //     if (container != null) {
        //         container.setEnvFrom(Collections.emptyList());
        //     }
        // }
        // if (workflow.getSpec().getTemplates() != null) {
        //     for (IoArgoprojWorkflowV1alpha1Template template : workflow.getSpec().getTemplates()) {
        //         template.setServiceAccountName(serviceAccountName);
        //         if (template.getContainer() != null) {
        //             template.getContainer().setEnvFrom(Collections.emptyList());
        //         }
        //     }
        // }

        Optional
            .ofNullable(workflow.getSpec().getTemplateDefaults())
            .ifPresent(templateDefaults -> {
                templateDefaults.setServiceAccountName(serviceAccountName);

                Optional
                    .ofNullable(templateDefaults.getContainer())
                    .ifPresent(container -> {
                        container.setEnvFrom(Collections.emptyList());
                    });
            });

        Optional
            .ofNullable(workflow.getSpec().getTemplates())
            .ifPresent(templates -> {
                templates.forEach(template -> {
                    template.setServiceAccountName(serviceAccountName);
                    Optional
                        .ofNullable(template.getContainer())
                        .ifPresent(container -> {
                            container.setEnvFrom(Collections.emptyList());
                        });
                });
            });

        workflow.getSpec().setServiceAccountName(serviceAccountName);
    }

    private void appendArtifactRepository(IoArgoprojWorkflowV1alpha1Workflow workflow) {
        if (StringUtils.hasText(artifactRepositoryConfigMap) || StringUtils.hasText(artifactRepositoryKey)) {
            IoArgoprojWorkflowV1alpha1ArtifactRepositoryRef ref = new IoArgoprojWorkflowV1alpha1ArtifactRepositoryRef()
                .configMap(artifactRepositoryConfigMap)
                .key(artifactRepositoryKey);
            workflow.getSpec().setArtifactRepositoryRef(ref);
        }
    }

    private void appendTemplateDefaults(IoArgoprojWorkflowV1alpha1Workflow workflow, K8sArgoWorkflowRunnable runnable)
        throws K8sFrameworkException {
        // Prepare environment variables for the Kubernetes job
        IoArgoprojWorkflowV1alpha1Template templateDefaults = workflow.getSpec().getTemplateDefaults();
        if (templateDefaults == null) {
            templateDefaults = new IoArgoprojWorkflowV1alpha1Template();
            workflow.getSpec().setTemplateDefaults(templateDefaults);
        }

        if (runnable.getEnvs() == null) {
            runnable.setEnvs(Collections.emptyList());
        }

        V1Container container = templateDefaults.getContainer();
        if (container == null) {
            container = new V1Container();
            templateDefaults.setContainer(container);
        }
        List<V1EnvFromSource> envFrom = new LinkedList<>(buildEnvFrom(runnable));
        List<V1EnvVar> env = new LinkedList<>(buildEnv(runnable));
        if (container.getEnv() != null) {
            env.addAll(container.getEnv());
        }
        if (container.getEnvFrom() != null) {
            envFrom.addAll(container.getEnvFrom());
        }

        container.envFrom(envFrom).env(env).resources(buildResources(runnable));
        container.securityContext(buildSecurityContext(runnable));

        V1PodSecurityContext securityContext = buildPodSecurityContext(runnable);

        templateDefaults
            // .automountServiceAccountToken(false)
            .securityContext(securityContext)
            .container(container);

        if (templateDefaults.getExecutor() == null) {
            templateDefaults.setExecutor(new IoArgoprojWorkflowV1alpha1ExecutorConfig());
        }
        // templateDefaults.getExecutor().setServiceAccountName(serviceAccountName);

        Optional
            .ofNullable(workflow.getSpec().getTemplates())
            .ifPresent(templates -> {
                templates.forEach(template -> {
                    template.securityContext(securityContext);
                    // .automountServiceAccountToken(false)
                });
            });
    }

    public V1SecurityContext buildSecurityContext(K8sArgoWorkflowRunnable runnable) throws K8sFrameworkException {
        V1SecurityContext context = super.buildSecurityContext(runnable);

        //override user when configured
        if (runAsUser != null && runAsUser != 0) {
            context.setRunAsUser((long) runAsUser);
        }

        return context;
    }

    public V1PodSecurityContext buildPodSecurityContext(K8sArgoWorkflowRunnable runnable) throws K8sFrameworkException {
        V1PodSecurityContext context = super.buildPodSecurityContext(runnable);

        //override user when configured
        if (runAsUser != null && runAsUser != 0) {
            context.setRunAsUser((long) runAsUser);
        }

        return context;
    }

    /*
     * K8s
     */
    public K8sWorkflowObject apply(@NotNull K8sWorkflowObject workflow) throws K8sFrameworkException {
        return workflow;
    }

    public K8sWorkflowObject get(@NotNull K8sWorkflowObject workflow) throws K8sFrameworkException {
        Assert.notNull(workflow.getMetadata(), "metadata can not be null");

        try {
            String wfName = workflow.getMetadata().getName();
            log.debug("get Argo Workflow for {}", wfName);

            Object object = customObjectsApi.getNamespacedCustomObject(
                ARGO_GROUP,
                ARGO_VERSION,
                namespace,
                ARGO_PLURAL,
                wfName
            );
            IoArgoprojWorkflowV1alpha1Workflow readWorkflow = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
                object,
                IoArgoprojWorkflowV1alpha1Workflow.class
            );
            return new K8sWorkflowObject(readWorkflow);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    public K8sWorkflowObject create(K8sWorkflowObject workflow) throws K8sFrameworkException {
        Assert.notNull(workflow.getMetadata(), "metadata can not be null");

        try {
            String workflowName = workflow.getMetadata().getName();
            log.debug("create Argo Workflow for {}", workflowName);

            //dispatch job via api
            Object created = customObjectsApi.createNamespacedCustomObject(
                ARGO_GROUP,
                ARGO_VERSION,
                namespace,
                ARGO_PLURAL,
                workflow.getWorkflow(),
                "true",
                null,
                null,
                null
            );
            IoArgoprojWorkflowV1alpha1Workflow createdWorkflow = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
                created,
                IoArgoprojWorkflowV1alpha1Workflow.class
            );
            return new K8sWorkflowObject(createdWorkflow);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getResponseBody());
            if (log.isDebugEnabled()) {
                log.debug("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    public void delete(K8sWorkflowObject workflow) throws K8sFrameworkException {
        Assert.notNull(workflow.getMetadata(), "metadata can not be null");

        try {
            String wfName = workflow.getMetadata().getName();
            log.debug("delete Argo Workflow for {}", wfName);
            customObjectsApi.deleteNamespacedCustomObject(
                ARGO_GROUP,
                ARGO_VERSION,
                namespace,
                ARGO_PLURAL,
                wfName,
                null,
                null,
                "Foreground",
                null,
                null
            );
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getResponseBody());
            if (log.isDebugEnabled()) {
                log.debug("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    @Override
    public List<V1Pod> pods(K8sWorkflowObject workflow) throws K8sFrameworkException {
        if (workflow == null || workflow.getMetadata() == null) {
            return null;
        }

        //fetch labels to select pods
        String workflowName = workflow.getMetadata().getName();

        String labelSelector = "workflows.argoproj.io/workflow=" + workflowName;
        try {
            log.debug("load pods for {}", labelSelector);
            V1PodList pods = coreV1Api.listNamespacedPod(
                namespace,
                null,
                null,
                null,
                null,
                labelSelector,
                null,
                null,
                null,
                null,
                null,
                null
            );

            return pods.getItems();
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    @Override
    public List<V1PersistentVolumeClaim> buildPersistentVolumeClaims(K8sArgoWorkflowRunnable runnable)
        throws K8sFrameworkException {
        List<V1PersistentVolumeClaim> volumes = super.buildPersistentVolumeClaims(runnable);
        if (volumes != null) {
            for (V1PersistentVolumeClaim pvc : volumes) {
                if (pvc.getMetadata() == null) {
                    pvc.setMetadata(new V1ObjectMeta());
                }

                //add workflow label to pvcs
                //workflow name is == runnable.id (see build)
                Map<String, String> wl = Map.of("workflows.argoproj.io/workflow", runnable.getId());
                Map<String, String> labels = MapUtils.mergeMultipleMaps(wl, pvc.getMetadata().getLabels());
                pvc.getMetadata().setLabels(labels);

                //set access mode+storage class
                if (pvc.getSpec() != null) {
                    pvc.getSpec().setAccessModes(List.of(pvcAccessMode));
                    if (StringUtils.hasText(pvcStorageClass)) {
                        pvc.getSpec().setStorageClassName(pvcStorageClass);
                    }
                }
            }
        }

        return volumes;
    }
}
