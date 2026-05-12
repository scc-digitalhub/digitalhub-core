/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.infrastructure.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;

import it.smartcommunitylabdhub.framework.k8s.config.KubernetesProperties;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.ray.model.ClusterModel;
import it.smartcommunitylabdhub.framework.ray.model.PodModel;
import it.smartcommunitylabdhub.framework.ray.model.RayJobModel;
import it.smartcommunitylabdhub.framework.ray.model.WorkerGroupModel;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayJobRunnable;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link K8sRayJobFramework} that exercises the {@code run()}
 * pipeline starting from various {@link K8sRayJobRunnable} variants.
 *
 * <p>The Kubernetes API is mocked so no real cluster is required. The test captures the
 * {@link DynamicKubernetesObject} (the RayJob CR) that the framework hands to the dynamic
 * Kubernetes client and asserts on its structure. The CR is also serialized to YAML and
 * printed to stdout so it can be applied to a real Ray operator deployment for verification.
 */
class K8sRayJobFrameworkIT {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper();

    private DynamicKubernetesApi dynamicApi;
    private TestableK8sRayJobFramework framework;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        ApiClient apiClient = new ApiClient();

        // mock the dynamic Kubernetes API so create() round-trips the CR back to us
        dynamicApi = mock(DynamicKubernetesApi.class);
        KubernetesApiResponse<DynamicKubernetesObject> response = mock(KubernetesApiResponse.class);
        when(response.isSuccess()).thenReturn(true);
        when(dynamicApi.create(anyString(), any(DynamicKubernetesObject.class), any()))
            .thenAnswer(inv -> {
                DynamicKubernetesObject cr = inv.getArgument(1);
                when(response.getObject()).thenReturn(cr);
                return response;
            });

        framework = new TestableK8sRayJobFramework(apiClient, dynamicApi);
        framework.setNamespace("test-ns");
        framework.setActiveDeadlineSeconds(7200);
        framework.setInitImage("registry.local/init:latest");
        framework.setApiGroup("ray.io");
        framework.setApiVersion("v1");
        framework.setSuspend(false);
        framework.setServiceType("ClusterIP");
        framework.setCollectResults("default");

        // minimal k8sProperties / applicationProperties (only used by overridden helpers
        // when paths require them; we still inject a shared volume to satisfy super.afterPropertiesSet)
        KubernetesProperties props = new KubernetesProperties();
        props.setSharedVolume(
            new CoreVolume(CoreVolume.VolumeType.empty_dir, "/shared", "shared-dir", Map.of("sizeLimit", "500Mi"))
        );
        framework.setK8sProperties(props);
    }

    // ---------- tests ----------

    @Test
    @DisplayName("Minimal RayJob runnable: head + single worker group, no envs/volumes")
    void minimalRayJob() throws Exception {
        K8sRayJobRunnable runnable = baseRunnable("run-min-1");
        runnable.setSpec(rayJobModel("python /shared/main.py", null, null, null));

        K8sRayJobRunnable result = framework.run(runnable);
        DynamicKubernetesObject cr = framework.lastCreatedCr;

        printCr("minimal RayJob", cr);

        // metadata
        assertEquals("ray.io/v1", cr.getApiVersion());
        assertEquals("RayJob", cr.getKind());
        assertEquals("run-min-1", cr.getMetadata().getName());
        assertEquals("test-ns", cr.getMetadata().getNamespace());

        // spec assertions
        JsonNode spec = crSpec(cr);
        assertEquals("run-min-1", spec.get("jobId").asText());
        assertEquals(7200, spec.get("activeDeadlineSeconds").asInt());
        assertEquals(0, spec.get("backoffLimit").asInt());
        assertEquals("K8sJobMode", spec.get("submissionMode").asText());
        assertEquals("python /shared/main.py", spec.get("entrypoint").asText());
        assertTrue(spec.get("runtimeEnvYAML").asText().contains("working_dir"));

        // cluster spec
        JsonNode cluster = spec.get("rayClusterSpec");
        assertNotNull(cluster);
        assertEquals("2.9.0", cluster.get("rayVersion").asText());
        assertNotNull(cluster.get("headGroupSpec"));
        JsonNode workers = cluster.get("workerGroupSpecs");
        assertTrue(workers.isArray());
        assertEquals(1, workers.size());
        assertEquals("workers-cpu", workers.get(0).get("groupName").asText());
        assertEquals(1, workers.get(0).get("replicas").asInt());

        // runnable lifecycle
        assertEquals("PENDING", result.getState());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("RayJob"));
    }

    @Test
    @DisplayName("RayJob with custom backoff/ttl and shutdownAfterJobFinishes flag")
    void customJobLifecycleFlags() throws Exception {
        K8sRayJobRunnable runnable = baseRunnable("run-life-1");
        RayJobModel m = rayJobModel("python -c 'print(1)'", null, null, null);
        m.setBackoffLimit(3);
        m.setShutdownAfterJobFinishes(Boolean.TRUE);
        m.setTtlSecondsAfterFinished(600);
        m.setPreRunningDeadlineSeconds(120);
        runnable.setSpec(m);

        framework.run(runnable);
        DynamicKubernetesObject cr = framework.lastCreatedCr;
        printCr("custom lifecycle RayJob", cr);

        JsonNode spec = crSpec(cr);
        assertEquals(3, spec.get("backoffLimit").asInt());
        assertEquals(true, spec.get("shutdownAfterJobFinishes").asBoolean());
        assertEquals(600, spec.get("ttlSecondsAfterFinished").asInt());
        assertEquals(120, spec.get("preRunningDeadlineSeconds").asInt());
    }

    @Test
    @DisplayName("RayJob with cluster selector targets an existing RayCluster")
    void clusterSelectorVariant() throws Exception {
        K8sRayJobRunnable runnable = baseRunnable("run-sel-1");
        RayJobModel m = rayJobModel(
            "python /shared/job.py",
            Map.of("ray.io/cluster", "shared-cluster"),
            null,
            null
        );
        runnable.setSpec(m);

        framework.run(runnable);
        DynamicKubernetesObject cr = framework.lastCreatedCr;
        printCr("clusterSelector RayJob", cr);

        JsonNode spec = crSpec(cr);
        assertEquals("shared-cluster", spec.get("clusterSelector").get("ray.io/cluster").asText());
    }

    @Test
    @DisplayName("RayJob with pip dependency spec is rendered into runtimeEnvYAML")
    void dependencySpecVariant() throws Exception {
        K8sRayJobRunnable runnable = baseRunnable("run-dep-1");
        runnable.setSpec(
            rayJobModel(
                "python /shared/main.py",
                null,
                "pip",
                (Serializable) (java.util.ArrayList<String>) new java.util.ArrayList<>(
                    List.of("numpy==1.26.0", "pandas")
                )
            )
        );

        framework.run(runnable);
        DynamicKubernetesObject cr = framework.lastCreatedCr;
        printCr("pip-deps RayJob", cr);

        JsonNode spec = crSpec(cr);
        String yaml = spec.get("runtimeEnvYAML").asText();
        assertTrue(yaml.contains("pip"), "runtimeEnvYAML must include pip block, got: " + yaml);
        assertTrue(yaml.contains("numpy==1.26.0"));
        assertTrue(yaml.contains("pandas"));
    }

    @Test
    @DisplayName("RayJob with multiple worker groups including autoscaling bounds")
    void multipleWorkerGroups() throws Exception {
        K8sRayJobRunnable runnable = baseRunnable("run-multi-1");
        ClusterModel cluster = baseCluster();
        PodModel gpuWorker = basePod("rayproject/ray-ml:2.9.0-gpu");
        cluster.setWorkerGroups(
            List.of(
                workerGroup("workers-cpu", 2, 1, 4, basePod("rayproject/ray:2.9.0")),
                workerGroup("workers-gpu", 1, 0, 2, gpuWorker)
            )
        );

        RayJobModel m = rayJobModel("python /shared/main.py", null, null, null);
        m.setCluster(cluster);
        runnable.setSpec(m);

        framework.run(runnable);
        DynamicKubernetesObject cr = framework.lastCreatedCr;
        printCr("multi-worker-groups RayJob", cr);

        JsonNode workers = crSpec(cr).get("rayClusterSpec").get("workerGroupSpecs");
        assertEquals(2, workers.size());
        assertEquals("workers-cpu", workers.get(0).get("groupName").asText());
        assertEquals(4, workers.get(0).get("maxReplicas").asInt());
        assertEquals("workers-gpu", workers.get(1).get("groupName").asText());
        assertEquals(0, workers.get(1).get("minReplicas").asInt());
    }

    @Test
    @DisplayName("RayJob with envs and labels propagates them into runtimeEnvYAML and CR labels")
    void envsAndLabelsVariant() throws Exception {
        K8sRayJobRunnable runnable = baseRunnable("run-env-1");
        runnable.setEnvs(
            List.of(
                new it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv("FOO", "bar"),
                new it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv("BAZ", "qux")
            )
        );
        runnable.setLabels(List.of(new CoreLabel("team", "ml")));

        RayJobModel m = rayJobModel(
            "python /shared/main.py",
            Map.of("ray.io/cluster", "ext"),
            null,
            null
        );
        runnable.setSpec(m);

        framework.run(runnable);
        DynamicKubernetesObject cr = framework.lastCreatedCr;
        printCr("envs+labels RayJob", cr);

        // labels are produced by the test override; we just verify the CR carries the (test) base labels
        Map<String, String> labels = cr.getMetadata().getLabels();
        assertNotNull(labels);
        assertEquals("ray", labels.get("app"));

        // env_vars only added when clusterSelector is set (per buildEnvYAML)
        String yaml = crSpec(cr).get("runtimeEnvYAML").asText();
        assertTrue(yaml.contains("env_vars"), "runtimeEnvYAML should contain env_vars: " + yaml);
        assertTrue(yaml.contains("FOO"));
        assertTrue(yaml.contains("BAZ"));
    }

    // ---------- helpers ----------

    private K8sRayJobRunnable baseRunnable(String id) {
        K8sRayJobRunnable r = new K8sRayJobRunnable();
        r.setId(id);
        r.setProject("demo");
        r.setRuntime("python");
        r.setTask("job");
        r.setUser("alice");
        return r;
    }

    private RayJobModel rayJobModel(String entrypoint, Map<String, String> selector, String depFmt, Serializable depSpec) {
        RayJobModel m = new RayJobModel();
        m.setCluster(baseCluster());
        m.setEntrypoint(entrypoint);
        m.setClusterSelector(selector);
        m.setDependencyFormat(depFmt);
        m.setDependencySpec(depSpec);
        return m;
    }

    private ClusterModel baseCluster() {
        ClusterModel c = new ClusterModel();
        c.setVersion("2.9.0");
        c.setHeadSpec(basePod("rayproject/ray:2.9.0"));
        c.setWorkerGroups(List.of(workerGroup("workers-cpu", 1, 1, 2, basePod("rayproject/ray:2.9.0"))));
        return c;
    }

    private WorkerGroupModel workerGroup(String name, int replicas, int min, int max, PodModel pod) {
        WorkerGroupModel w = new WorkerGroupModel();
        w.setName(name);
        w.setReplicas(replicas);
        w.setMinReplicas(min);
        w.setMaxReplicas(max);
        w.setWorkerSpec(pod);
        return w;
    }

    private PodModel basePod(String image) {
        return PodModel
            .builder()
            .image(image)
            .startParams(Map.of("dashboard-host", "0.0.0.0"))
            .rayResources(Map.of("CPU", "1"))
            .build();
    }

    private JsonNode crSpec(DynamicKubernetesObject cr) throws Exception {
        // dynamic CR raw is a Gson JsonElement; convert via JSON string
        String json = new Gson().toJson(cr.getRaw());
        return JSON.readTree(json).get("spec");
    }

    private void printCr(String label, DynamicKubernetesObject cr) throws Exception {
        String json = new Gson().toJson(cr.getRaw());
        JsonNode root = JSON.readTree(json);
        // ensure top-level fields apiVersion/kind/metadata are present for cluster-side apply
        com.fasterxml.jackson.databind.node.ObjectNode out = JSON.createObjectNode();
        out.put("apiVersion", cr.getApiVersion());
        out.put("kind", cr.getKind());
        out.set("metadata", JSON.valueToTree(cr.getMetadata()));
        out.set("spec", root.get("spec"));
        String yaml = YAML.writerWithDefaultPrettyPrinter().writeValueAsString(out);
        System.out.println("# ===== " + label + " =====");
        System.out.println(yaml);
        System.out.println();
    }

    // ---------- testable subclass ----------

    /**
     * Subclass that bypasses Spring wiring and the helpers that depend on cluster resources
     * (secret/configmap/PVC creation, env builders, label helper, etc.). It captures the
     * {@link DynamicKubernetesObject} sent to the dynamic client so tests can assert on it.
     */
    static class TestableK8sRayJobFramework extends K8sRayJobFramework {

        private final DynamicKubernetesApi mockedApi;
        DynamicKubernetesObject lastCreatedCr;

        TestableK8sRayJobFramework(ApiClient apiClient, DynamicKubernetesApi mockedApi) {
            super(apiClient);
            this.mockedApi = mockedApi;
        }

        @Override
        public void afterPropertiesSet() {
            // skip Spring lifecycle; everything we need is set explicitly in the test
        }

        @Override
        public DynamicKubernetesApi getDynamicKubernetesApi() {
            return new DynamicKubernetesApi("ray.io", "v1", "rayjobs", apiClient) {
                @Override
                public KubernetesApiResponse<DynamicKubernetesObject> create(
                    String namespace,
                    DynamicKubernetesObject cr,
                    io.kubernetes.client.util.generic.options.CreateOptions options
                ) {
                    lastCreatedCr = cr;
                    return mockedApi.create(namespace, cr, options);
                }
            };
        }

        @Override
        protected Map<String, String> buildLabels(K8sRayJobRunnable runnable) {
            return Map.of(
                "app", "ray",
                "ray.io/run-id", runnable.getId() == null ? "unknown" : runnable.getId()
            );
        }

        @Override
        protected List<V1EnvVar> buildEnv(K8sRayJobRunnable runnable) {
            return Collections.emptyList();
        }

        @Override
        protected List<V1EnvFromSource> buildEnvFrom(K8sRayJobRunnable runnable) {
            return Collections.emptyList();
        }

        @Override
        protected V1ResourceRequirements buildResources(K8sRayJobRunnable runnable) {
            return new V1ResourceRequirements();
        }

        @Override
        protected List<V1Volume> buildVolumes(K8sRayJobRunnable runnable) {
            return Collections.emptyList();
        }

        @Override
        protected List<V1VolumeMount> buildVolumeMounts(K8sRayJobRunnable runnable) {
            return Collections.emptyList();
        }

        @Override
        public V1SecurityContext buildSecurityContext(K8sRayJobRunnable runnable) {
            return null;
        }

        @Override
        public V1PodSecurityContext buildPodSecurityContext(K8sRayJobRunnable runnable) {
            return null;
        }

        @Override
        protected Map<String, String> buildNodeSelector(K8sRayJobRunnable runnable) {
            return null;
        }

        @Override
        public V1Affinity buildAffinity(K8sRayJobRunnable runnable) {
            return null;
        }

        @Override
        protected List<V1Toleration> buildTolerations(K8sRayJobRunnable runnable) {
            return null;
        }

        @Override
        public String buildRuntimeClassName(K8sRayJobRunnable runnable) {
            return null;
        }

        @Override
        public String buildPriorityClassName(K8sRayJobRunnable runnable) {
            return null;
        }

        @Override
        protected List<V1LocalObjectReference> buildImagePullSecrets(K8sRayJobRunnable runnable) {
            return null;
        }

        @Override
        protected V1Secret buildRunSecret(K8sRayJobRunnable runnable) {
            return null;
        }

        @Override
        public V1ConfigMap buildInitConfigMap(K8sRayJobRunnable runnable) {
            return null;
        }

        @Override
        public List<V1PersistentVolumeClaim> buildPersistentVolumeClaims(K8sRayJobRunnable runnable) {
            return null;
        }
    }
}
