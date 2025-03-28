package it.smartcommunitylabdhub.runtime.huggingface;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.enums.RelationshipName;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.metadata.RelationshipsMetadata;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.ModelService;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.runtime.huggingface.specs.HuggingfaceServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.huggingface.specs.HuggingfaceServeRunSpec;
import it.smartcommunitylabdhub.runtime.huggingface.specs.HuggingfaceServeTaskSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class HuggingfaceServeRunner {

    private static final int HTTP_PORT = 8080;
    private static final int GRPC_PORT = 8081;

    private final String image;
    private final HuggingfaceServeFunctionSpec functionSpec;
    private final Map<String, String> secretData;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final ModelService modelService;

    public HuggingfaceServeRunner(
        String image,
        HuggingfaceServeFunctionSpec functionSpec,
        Map<String, String> secretData,
        K8sBuilderHelper k8sBuilderHelper,
        ModelService modelService
    ) {
        this.image = image;
        this.functionSpec = functionSpec;
        this.secretData = secretData;
        this.k8sBuilderHelper = k8sBuilderHelper;
        this.modelService = modelService;
    }

    public K8sRunnable produce(Run run) {
        HuggingfaceServeRunSpec runSpec = HuggingfaceServeRunSpec.with(run.getSpec());
        HuggingfaceServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        //read source and build context
        List<ContextRef> contextRefs = null;
        String path = functionSpec.getPath();
        if (path.startsWith(Keys.STORE_PREFIX)) {
            KeyAccessor keyAccessor = KeyAccessor.with(path);
            if (!EntityName.MODEL.getValue().equals(keyAccessor.getType())) {
                throw new CoreRuntimeException("invalid entity kind reference, expected model");
            }
            Model model = keyAccessor.getId() != null
                ? modelService.findModel(keyAccessor.getId())
                : modelService.getLatestModel(keyAccessor.getProject(), keyAccessor.getName());
            if (model == null) {
                throw new CoreRuntimeException("invalid entity reference, HuggingFace model not found");
            }
            if (!model.getKind().equals("huggingface") && !model.getKind().equals("hf")) {
                throw new CoreRuntimeException("invalid entity reference, expected HuggingFace model");
            }
            RelationshipDetail rel = new RelationshipDetail();
            rel.setType(RelationshipName.CONSUMES);
            rel.setDest(run.getKey());
            rel.setSource(model.getKey());
            RelationshipsMetadata relationships = RelationshipsMetadata.from(run.getMetadata());
            relationships.getRelationships().add(rel);
            run.getMetadata().putAll(relationships.toMap());
            path = (String) model.getSpec().get("path");
        }

        UriComponents uri = UriComponentsBuilder.fromUriString(path).build();

        List<String> args = new ArrayList<>(
            List.of(
                "-m",
                "huggingfaceserver",
                "--model_name",
                StringUtils.hasText(functionSpec.getModelName()) ? functionSpec.getModelName() : "model",
                "--protocol",
                "v2",
                "--enable_docs_url",
                "true"
            )
        );

        // model dir or model id
        if ("huggingface".equals(uri.getScheme()) || "hf".equals(uri.getScheme())) {
            String mdlId = uri.getHost() + uri.getPath();
            String revision = null;
            if (mdlId.contains(":")) {
                String[] parts = mdlId.split(":");
                mdlId = parts[0];
                revision = parts[1];
            }
            args.add("--model_id");
            args.add(mdlId);
            if (revision != null) {
                args.add("--model_revision");
                args.add(revision);
            }
        } else {
            args.add("--model_dir");
            args.add("/shared/model");

            contextRefs =
                Collections.singletonList(
                    ContextRef.builder().source(path).protocol(uri.getScheme()).destination("model").build()
                );
        }

        // tokenizer revision
        if (StringUtils.hasText(taskSpec.getTokenizerRevision())) {
            args.add("--tokenizer_revision");
            args.add(taskSpec.getTokenizerRevision());
        }
        // max length
        if (taskSpec.getMaxLength() != null) {
            args.add("--max_length");
            args.add(taskSpec.getMaxLength().toString());
        }
        // disable_lower_case
        if (taskSpec.getDisableLowerCase() != null) {
            args.add("--disable_lower_case");
            args.add(taskSpec.getDisableLowerCase().toString());
        }
        // disable_special_tokens
        if (taskSpec.getDisableSpecialTokens() != null) {
            args.add("--disable_special_tokens");
            args.add(taskSpec.getDisableSpecialTokens().toString());
        }
        // trust_remote_code
        if (taskSpec.getTrustRemoteCode() != null) {
            args.add("--trust_remote_code");
            args.add(taskSpec.getTrustRemoteCode().toString());
        } else {
            args.add("--trust_remote_code");
            args.add("true");
        }
        // tensor_input_names
        if (taskSpec.getTensorInputNames() != null) {
            args.add("--tensor_input_names");
            args.add(StringUtils.collectionToCommaDelimitedString(taskSpec.getTensorInputNames()));
        }
        // task
        if (taskSpec.getHuggingfaceTask() != null) {
            args.add("--task");
            args.add(taskSpec.getHuggingfaceTask().getTask());
        }
        // backend
        if (taskSpec.getBackend() != null) {
            args.add("--backend");
            args.add(taskSpec.getBackend().getBackend());
        }
        // return_token_type_ids
        if (taskSpec.getReturnTokenTypeIds() != null) {
            args.add("--return_token_type_ids");
            args.add(taskSpec.getReturnTokenTypeIds().toString());
        }
        // return_probabilities
        if (taskSpec.getReturnProbabilities() != null) {
            args.add("--return_probabilities");
            args.add(taskSpec.getReturnProbabilities().toString());
        }
        // disable_log_requests
        if (taskSpec.getDisableLogRequests() != null) {
            args.add("--disable_log_requests");
            args.add(taskSpec.getDisableLogRequests().toString());
        }
        // max_log_len
        if (taskSpec.getMaxLogLen() != null) {
            args.add("--max_log_len");
            args.add(taskSpec.getMaxLogLen().toString());
        }
        // dtype
        if (taskSpec.getDtype() != null) {
            args.add("--dtype");
            args.add(taskSpec.getDtype().getDType());
        }

        // if (functionSpec.getAdapters() != null && functionSpec.getAdapters().size() > 0) {
        //     contextRefs = new LinkedList<>(contextRefs);
        //     args.add("--enable-lora");
        //     args.add("--lora-modules");

        //     for (Map.Entry<String, String> adapter : functionSpec.getAdapters().entrySet()) {
        //         UriComponents adapterUri = UriComponentsBuilder.fromUriString(adapter.getValue()).build();
        //         String adapterSource = adapter.getValue().trim();
        //         String ref = adapterSource;

        //         if (!"huggingface".equals(adapterUri.getScheme())) {
        //             if (!adapterSource.endsWith("/")) adapterSource += "/";
        //             ref = "/shared/adapters/" + adapter.getKey() + "/";
        //             contextRefs =
        //                 Collections.singletonList(
        //                     ContextRef
        //                         .builder()
        //                         .source(adapterSource)
        //                         .protocol(adapterUri.getScheme())
        //                         .destination("adapters/" + adapter.getKey())
        //                         .build()
        //                 );
        //         }
        //         args.add(adapter.getKey() +  "=" + ref);
        //     }
        // }

        CorePort servicePort = new CorePort(HTTP_PORT, HTTP_PORT);
        CorePort grpcPort = new CorePort(GRPC_PORT, GRPC_PORT);

        String img = StringUtils.hasText(functionSpec.getImage()) ? functionSpec.getImage() : image;

        //validate image
        if (img == null || !img.startsWith(HuggingfaceServeRuntime.IMAGE)) {
            throw new IllegalArgumentException(
                "invalid or empty image, must be based on " + HuggingfaceServeRuntime.IMAGE
            );
        }

        //build runnable
        K8sRunnable k8sServeRunnable = K8sServeRunnable
            .builder()
            .runtime(HuggingfaceServeRuntime.RUNTIME)
            .task(HuggingfaceServeTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(img)
            .command("python")
            .args(args.toArray(new String[0]))
            .contextRefs(contextRefs)
            .envs(coreEnvList)
            .secrets(coreSecrets)
            .resources(taskSpec.getResources())
            .volumes(taskSpec.getVolumes())
            .nodeSelector(taskSpec.getNodeSelector())
            .affinity(taskSpec.getAffinity())
            .tolerations(taskSpec.getTolerations())
            .runtimeClass(taskSpec.getRuntimeClass())
            .priorityClass(taskSpec.getPriorityClass())
            .template(taskSpec.getProfile())
            //specific
            .replicas(taskSpec.getReplicas())
            .servicePorts(List.of(servicePort, grpcPort))
            .serviceType(taskSpec.getServiceType() != null ? taskSpec.getServiceType() : CoreServiceType.NodePort)
            .build();

        k8sServeRunnable.setId(run.getId());
        k8sServeRunnable.setProject(run.getProject());

        return k8sServeRunnable;
    }
}
