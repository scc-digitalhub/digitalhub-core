/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.security.service;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.services.TaskService;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.extensions.ExtensionManager;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.extensions.persistence.ExtensionBuilder;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipName;
import it.smartcommunitylabdhub.relationships.RelationshipsMetadata;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.RunManager;
import it.smartcommunitylabdhub.runs.lifecycle.RunEvent;
import it.smartcommunitylabdhub.security.config.SecurityExtensionProperties;
import it.smartcommunitylabdhub.security.model.SecurityAnalyzer;
import it.smartcommunitylabdhub.security.model.SecurityAssessment;
import it.smartcommunitylabdhub.security.model.SecurityReportState;
import it.smartcommunitylabdhub.security.specs.SecurityReportSpec;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Orchestrates security analysis of functions: provisions the predefined analyzer function+task in the project,
 * launches the analysis run and keeps the security report extension attached to the analyzed function in sync.
 */
@Service
@Slf4j
public class SecurityAnalysisService {

    public static final String ANALYZER_LABEL = "security-analyzer";
    public static final String REPORT_METADATA_KEY = "security_report";
    public static final String SUMMARY_FILE = "summary.json";


    private final SecurityExtensionProperties properties;
    private final SecurityAnalyzerRegistry registry;
    
    private FunctionManager functionManager;
    private TaskService taskService;
    private RunManager runManager;
    private LifecycleManager<Run> runLifecycleManager;
    private ExtensionManager extensionManager;

    public SecurityAnalysisService(SecurityExtensionProperties properties, SecurityAnalyzerRegistry registry) {
        Assert.notNull(properties, "properties are required");
        Assert.notNull(registry, "registry is required");
        this.properties = properties;
        this.registry = registry;
    }

    @Autowired
    public void setFunctionManager(FunctionManager functionManager) {
        this.functionManager = functionManager;
    }

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Autowired
    public void setRunManager(RunManager runManager) {
        this.runManager = runManager;
    }

    @Autowired
    public void setRunLifecycleManager(@Lazy LifecycleManager<Run> runLifecycleManager) {
        this.runLifecycleManager = runLifecycleManager;
    }

    @Autowired
    public void setExtensionManager(ExtensionManager extensionManager) {
        this.extensionManager = extensionManager;
    }
    /*
     * Analysis
     */

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isAnalyzer(Function function) {
        if (registry.isAnalyzer(function.getName())) {
            return true;
        }

        BaseMetadata metadata = BaseMetadata.from(function.getMetadata());
        return metadata.getLabels() != null && metadata.getLabels().contains(ANALYZER_LABEL);
    }

    /**
     * Asynchronously analyze the given function with the configured analyzer.
     */
    public void analyze(Function function) {
        if (!isEnabled() || function == null || isAnalyzer(function)) {
            return;
        }

        registry.list().forEach(analyzer -> {
            log.debug("available security analyzer: {}", analyzer.getName());
            analyze(analyzer, function);
        });
    }

    private void analyze(SecurityAnalyzer analyzer, Function function) {
        log.debug("analyze function {} with {}", function.getId(), analyzer.getName());

        String project = function.getProject();
        String user = function.getUser();

        Extension report = null;
        try {
            report = initReport(analyzer, function);

            Function af = ensureFunction(analyzer, project, user);
            Task task = ensureTask(analyzer, af, user);

            Run run = buildRun(analyzer, af, task, function, report);
            run = runManager.createRun(run);
            log.debug("created security analysis run {} for function {}", run.getId(), function.getId());

            String runKey = run.getKey();
            updateReport(report.getId(), spec -> spec.setRun(runKey), null, null);

            run = runLifecycleManager.perform(run, RunEvent.BUILD.name());
            runLifecycleManager.perform(run, RunEvent.RUN.name());
        } catch (Exception e) {
            log.error("error analyzing function {}: {}", function.getId(), e.getMessage());
            if (report != null) {
                updateReport(report.getId(), null, SecurityReportState.ERROR, e.getMessage());
            }
        }
    }

    private Extension initReport(SecurityAnalyzer analyzer, Function function) throws Exception {
        String parent = ExtensionBuilder.from(function).getParent();

        SecurityReportSpec spec = new SecurityReportSpec();
        spec.setAnalyzer(analyzer.getName());
        spec.setTarget(function.getKey());
        spec.setImage(getImage(function));
        spec.setAssessment(SecurityAssessment.UNKNOWN);

        Map<String, Serializable> status = new HashMap<>(
            Map.of(Fields.STATE, SecurityReportState.PENDING.name())
        );

        List<Extension> existing = extensionManager.listExtensionsByParent(
            parent,
            SecurityReportSpec.KIND,
            analyzer.getName()
        );
        if (!existing.isEmpty()) {
            Extension ext = existing.get(0);
            ext.setSpec(spec.toMap());
            ext.setStatus(status);
            return extensionManager.updateExtension(ext.getId(), ext);
        }

        Extension ext = Extension.builder()
            .project(function.getProject())
            .entity(EntityUtils.getEntityName(Function.class))
            .parent(parent)
            .kind(SecurityReportSpec.KIND)
            .name(analyzer.getName())
            .user(function.getUser())
            .spec(spec.toMap())
            .status(status)
            .build();

        return extensionManager.createExtension(ext);
    }

    private Function ensureFunction(SecurityAnalyzer analyzer, String project, String user) throws Exception {
        try {
            return functionManager.getLatestFunctionByName(project, analyzer.getName());
        } catch (NoSuchEntityException e) {
            //not found, create
        }

        log.debug("create security analyzer function {} in project {}", analyzer.getName(), project);

        BaseMetadata metadata = new BaseMetadata();
        metadata.setName(analyzer.getName());
        metadata.setDescription(analyzer.getDescription());
        metadata.setLabels(Set.of(ANALYZER_LABEL));

        Function function = Function.builder()
            .project(project)
            .name(analyzer.getName())
            .kind(analyzer.getFunction().getKind())
            .user(user)
            .metadata(metadata.toMap())
            .spec(new HashMap<>(analyzer.getFunction().getSpec()))
            .build();

        return functionManager.createFunction(function);
    }

    private Task ensureTask(SecurityAnalyzer analyzer, Function function, String user) throws Exception {
        String kind = analyzer.getTask().getKind();
        Task task = functionManager
            .getTasksByFunctionId(function.getId())
            .stream()
            .filter(t -> kind.equals(t.getKind()))
            .findFirst()
            .orElse(null);

        if (task != null) {
            return task;
        }

        log.debug("create security analyzer task {} for function {}", kind, function.getId());

        Map<String, Serializable> spec = MapUtils.mergeMultipleMaps(
            analyzer.getTask().getSpec(),
            Map.of(Fields.FUNCTION, functionRef(function))
        );

        return taskService.createTask(
            Task.builder().project(function.getProject()).kind(kind).user(user).spec(spec).build()
        );
    }

    private Run buildRun(SecurityAnalyzer analyzer, Function af, Task task, Function target, Extension report) {
        //analysis context passed to the analyzer
        HashMap<String, Serializable> context = new HashMap<>();
        context.put("function_key", target.getKey());
        context.put("report_key", report.getKey());

        Map<String, Serializable> template = analyzer.getRun().getSpec();
        String field = analyzer.getParametersField();

        Map<String, Serializable> params = new HashMap<>();
        if (template.get(field) instanceof Map<?, ?> p) {
            p.forEach((k, v) -> params.put(String.valueOf(k), (Serializable) v));
        }
        params.putAll(context);

        Map<String, Serializable> spec = new HashMap<>(template);
        spec.put(field, new HashMap<>(params));
        spec.put(Fields.TASK, task.getKind() + "://" + task.getProject() + "/" + task.getId());
        spec.put(Fields.FUNCTION, functionRef(af));
        spec.put("local_execution", false);

        //track lineage and link back to the report
        List<RelationshipDetail> rels = new ArrayList<>();
        rels.add(new RelationshipDetail(RelationshipName.CONSUMES, null, target.getKey()));
        RelationshipsMetadata relMetadata = RelationshipsMetadata.builder().relationships(rels).build();
        Map<String, Serializable> metadata = MapUtils.mergeMultipleMaps(
            relMetadata.toMap(),
            Map.of(REPORT_METADATA_KEY, report.getId())
        );

        return Run.builder()
            .kind(analyzer.getRun().getKind())
            .project(af.getProject())
            .user(target.getUser())
            .spec(spec)
            .metadata(metadata)
            .status(new HashMap<>(Map.of(Fields.STATE, State.CREATED.name())))
            .build();
    }

    /*
     * Run tracking
     */

    public static @Nullable String getReportId(Run run) {
        if (run == null || run.getMetadata() == null) {
            return null;
        }

        Serializable id = run.getMetadata().get(REPORT_METADATA_KEY);
        return id instanceof String s && StringUtils.hasText(s) ? s : null;
    }
    /*
     * Helpers
     */

    private synchronized void updateReport(
        String id,
        @Nullable java.util.function.Consumer<SecurityReportSpec> updater,
        @Nullable SecurityReportState state,
        @Nullable String message
    ) {
        try {
            Extension ext = extensionManager.findExtension(id);
            if (ext == null) {
                log.warn("security report {} not found", id);
                return;
            }

            if (updater != null) {
                SecurityReportSpec spec = SecurityReportSpec.with(ext.getSpec());
                updater.accept(spec);
                ext.setSpec(spec.toMap());
            }

            Map<String, Serializable> status = new HashMap<>(
                ext.getStatus() != null ? ext.getStatus() : Collections.emptyMap()
            );
            if (state != null) {
                status.put(Fields.STATE, state.name());
                status.remove("message");
            }
            if (message != null) {
                status.put("message", message);
            }
            ext.setStatus(status);

            extensionManager.updateExtension(id, ext);
        } catch (Exception e) {
            log.error("error updating security report {}: {}", id, e.getMessage());
        }
    }

    private static @Nullable String getImage(Function function) {
        if (function.getSpec() == null) {
            return null;
        }

        Serializable image = function.getSpec().get("image");
        return image instanceof String s && StringUtils.hasText(s) ? s : null;
    }

    private static String functionRef(Function function) {
        return (
            function.getKind() + "://" + function.getProject() + "/" + function.getName() + ":" + function.getId()
        );
    }
}
