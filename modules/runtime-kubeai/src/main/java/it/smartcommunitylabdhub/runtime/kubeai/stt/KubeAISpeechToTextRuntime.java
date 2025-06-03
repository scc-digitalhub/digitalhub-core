package it.smartcommunitylabdhub.runtime.kubeai.stt;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.task.TaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.runtime.kubeai.base.KubeAIRuntime;
import it.smartcommunitylabdhub.runtime.kubeai.base.KubeAIServeRunStatus;
import it.smartcommunitylabdhub.runtime.kubeai.base.KubeAIServeRunner;
import it.smartcommunitylabdhub.runtime.kubeai.models.OpenAIService;
import it.smartcommunitylabdhub.runtime.kubeai.stt.specs.KubeAISpeechToTextFunctionSpec;
import it.smartcommunitylabdhub.runtime.kubeai.stt.specs.KubeAISpeechToTextRunSpec;
import it.smartcommunitylabdhub.runtime.kubeai.stt.specs.KubeAISpeechToTextServeTaskSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RuntimeComponent(runtime = KubeAISpeechToTextRuntime.RUNTIME)
public class KubeAISpeechToTextRuntime
    extends KubeAIRuntime<KubeAISpeechToTextFunctionSpec, KubeAISpeechToTextRunSpec> {

    public static final String RUNTIME = "kubeai-speech";
    private final String FEATURE = "SpeechToText";
    public static final String ENGINE = "FasterWhisper";

    public KubeAISpeechToTextRuntime() {
        super(KubeAISpeechToTextRunSpec.KIND);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // nothing to do
    }

    @Override
    public KubeAISpeechToTextRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!KubeAISpeechToTextRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(
                        String.valueOf(run.getKind()),
                        KubeAISpeechToTextRunSpec.KIND
                    )
            );
        }

        KubeAISpeechToTextFunctionSpec funSpec = KubeAISpeechToTextFunctionSpec.with(function.getSpec());
        KubeAISpeechToTextRunSpec runSpec = KubeAISpeechToTextRunSpec.with(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case KubeAISpeechToTextServeTaskSpec.KIND -> {
                    yield KubeAISpeechToTextServeTaskSpec.with(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        KubeAISpeechToTextRunSpec serveSpec = KubeAISpeechToTextRunSpec.with(map);

        //ensure function is not modified
        serveSpec.setFunctionSpec(funSpec);

        return serveSpec;
    }

    @Override
    public K8sCRRunnable run(@NotNull Run run) {
        //check run kind
        if (!KubeAISpeechToTextRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(
                        String.valueOf(run.getKind()),
                        KubeAISpeechToTextRunSpec.KIND
                    )
            );
        }

        if (k8sBuilderHelper == null || k8sSecretHelper == null) {
            throw new IllegalArgumentException("k8s helpers not available");
        }

        KubeAISpeechToTextRunSpec runSpec = KubeAISpeechToTextRunSpec.with(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        return switch (runAccessor.getTask()) {
            case KubeAISpeechToTextServeTaskSpec.KIND -> new KubeAIServeRunner(
                KubeAISpeechToTextRuntime.RUNTIME,
                ENGINE,
                List.of(FEATURE),
                runSpec.getFunctionSpec(),
                secretService.getSecretData(run.getProject(), runSpec.getSecrets()),
                k8sBuilderHelper,
                k8sSecretHelper,
                modelService
            )
                .produce(run);
            default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
        };
    }

    @Override
    public KubeAIServeRunStatus onRunning(@NotNull Run run, RunRunnable runnable) {
        KubeAIServeRunStatus status = KubeAIServeRunStatus.with(run.getStatus());
        KubeAISpeechToTextFunctionSpec functionSpec = KubeAISpeechToTextFunctionSpec.with(run.getSpec());
        if (status == null || functionSpec == null) {
            return null;
        }

        //build openapi descriptor only once
        if (status.getOpenai() == null) {
            //inflate super or rebuild
            OpenAIService openai = Optional
                .ofNullable(super.onRunning(run, runnable))
                .map(s -> s.getOpenai())
                .orElse(new OpenAIService());

            //set features and persist
            openai.setFeatures(List.of(FEATURE));
            status.setOpenai(openai);
        }

        //build service descriptor only once
        if (status.getService() == null) {
            //inflate super or rebuild
            K8sServiceInfo service = Optional
                .ofNullable(super.onRunning(run, runnable))
                .map(s -> s.getService())
                .orElse(new K8sServiceInfo());

            //feature based urls
            String baseUrl = kubeAiEndpoint + "/openai";
            List<String> urls = service.getUrls() != null ? new ArrayList<>(service.getUrls()) : new ArrayList<>();
            urls.add(baseUrl + "/v1/audio/transcriptions");

            service.setUrls(urls);
            status.setService(service);
        }

        return status;
    }
}
