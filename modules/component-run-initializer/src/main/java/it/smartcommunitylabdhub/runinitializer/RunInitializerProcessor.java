package it.smartcommunitylabdhub.runinitializer;

import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.extensions.ExtensionManager;
import it.smartcommunitylabdhub.extensions.persistence.ExtensionBuilder;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runinitializer.spec.FileRef;
import it.smartcommunitylabdhub.runinitializer.spec.RunInitializerSpec;
import it.smartcommunitylabdhub.runs.Run;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EffectType(stages = { "onReady" }, type = Run.class)
@Component
@Slf4j
public class RunInitializerProcessor implements Effect<Run> {

    @Autowired(required = false)
    private ExtensionManager extManager;

    @Override
    public K8sRunnable process(String stage, Run run, Serializable input) throws CoreRuntimeException {
        if (run == null || input == null) {
            return null;
        }

        if ((run.getExtensions() == null || run.getExtensions().isEmpty()) && extManager == null) {
            return null;
        }

        if (input instanceof K8sRunnable k8sRunnable) {
            try {
                //extensions are either inlined or we fetch
                List<RunInitializerSpec> specs = new ArrayList<>();

                if (run.getExtensions() != null) {
                    specs.addAll(
                        run
                            .getExtensions()
                            .stream()
                            .filter(
                                ext -> RunInitializerSpec.KIND.equals(ext.get("kind")) && ext.get("spec") instanceof Map
                            )
                            .map(ext -> RunInitializerSpec.with((Map<String, Serializable>) ext.get("spec")))
                            .toList()
                    );
                }

                if (specs.isEmpty() && extManager != null) {
                    String parentId = ExtensionBuilder.from(run).getParent();
                    extManager
                        .listExtensionsByParent(parentId, RunInitializerSpec.KIND)
                        .forEach(ext -> specs.add(RunInitializerSpec.with(ext.getSpec())));
                }

                if (!specs.isEmpty()) {
                    log.debug("Processing run {} with {} extensions", run.getId(), specs.size());

                    //patch contextRefs from extension
                    List<FileRef> refs = specs
                        .stream()
                        .flatMap(spec -> spec.getFiles().stream())
                        .toList();

                    if (log.isTraceEnabled()) {
                        log.trace("file refs: {}", refs);
                    }

                    List<ContextRef> contextRefs = k8sRunnable.getContextRefs();
                    Set<ContextRef> newRefs = new HashSet<>();
                    if (contextRefs != null) {
                        newRefs.addAll(contextRefs);
                    }
                    refs.forEach(re -> {
                        ContextRef ref = ContextRef.from(re.getSource());
                        ref.setDestination(re.getDestination());
                        newRefs.add(ref);
                    });

                    k8sRunnable.setContextRefs(List.copyOf(newRefs));

                    return k8sRunnable;
                }
            } catch (Exception e) {
                log.error("Error processing extensions for {}: {}", run.getId(), e.getMessage());
            }
        }

        return null;
    }
}
