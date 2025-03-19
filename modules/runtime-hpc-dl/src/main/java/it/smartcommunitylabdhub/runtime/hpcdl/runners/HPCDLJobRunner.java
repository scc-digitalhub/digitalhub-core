package it.smartcommunitylabdhub.runtime.hpcdl.runners;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;

import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.artifact.ArtifactBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.ArtifactService;
import it.smartcommunitylabdhub.files.service.FilesService;
import it.smartcommunitylabdhub.runtime.hpcdl.HPCDLRuntime;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.runnables.HPCDLRunnable;
import it.smartcommunitylabdhub.runtime.hpcdl.specs.HPCDLFunctionSpec;
import it.smartcommunitylabdhub.runtime.hpcdl.specs.HPCDLJobTaskSpec;
import it.smartcommunitylabdhub.runtime.hpcdl.specs.HPCDLRunSpec;

public class HPCDLJobRunner {

    private FilesService filesService;
    private ArtifactService artifactService;

    public HPCDLJobRunner(FilesService filesService, ArtifactService artifactService) {
        this.filesService = filesService;
        this.artifactService = artifactService;
    }

    public HPCDLRunnable produce(Run run) {
        HPCDLRunSpec runSpec = new HPCDLRunSpec(run.getSpec());
        HPCDLFunctionSpec functionSpec = runSpec.getFunctionSpec();
        if (functionSpec == null) {
            throw new IllegalArgumentException("functionSpec is null");
        }

        Map<String, String> inputs = new HashMap<>();
        if (runSpec.getInputs() != null) {
            for (String path : runSpec.getInputs().keySet()) {
                // convert artifact key to download url
                String artifactKey = runSpec.getInputs().get(path);
                KeyAccessor keyAccessor = KeyAccessor.with(artifactKey);
                Artifact artifact = artifactService.getArtifact(keyAccessor.getId());
                if (artifact != null) {
                    ArtifactBaseSpec spec = new ArtifactBaseSpec();
                    spec.configure(artifact.getSpec());

                    try {
                        filesService.getDownloadAsUrl(spec.getPath());
                    } catch (StoreException e) {
                        throw new IllegalArgumentException("cannot construct downoad url for artifact " + artifactKey);
                    }
                    inputs.put(path, artifactKey);
                }
            }
        }
        Map<String, String> outputs = new HashMap<>();
        Map<String, String> outputArtifacts = new HashMap<>();

        if (runSpec.getOutputs() != null) {
            // create artifact for each named output and generate upload URL for it
            for (String path : runSpec.getOutputs().keySet()) {
                String artifactName = runSpec.getOutputs().get(path);
                String id = generateKey();
                String targetPath =
                    filesService.getDefaultStore() +
                    "/" +
                    run.getProject() +
                    "/" +
                    EntityName.ARTIFACT.getValue() +
                    "/" +
                    id +
                    (path.startsWith("/") ? path : "/" + path);
                Artifact artifactDTO = new Artifact();
                artifactDTO.setProject(run.getProject());
                artifactDTO.setKind(EntityName.ARTIFACT.getValue());
                artifactDTO.setName(artifactName);
                artifactDTO.setId(id);
                artifactDTO.setUser(SecurityContextHolder.getContext().getAuthentication().getName());
                ArtifactBaseSpec spec = new ArtifactBaseSpec();
                spec.setPath(targetPath);
                Map<String, Serializable> status = new HashMap<>();
                status.put("state", State.PENDING.name());
                artifactDTO.setStatus(status);

                artifactDTO.setSpec(spec.toMap());
                
                try {
                    artifactDTO = artifactService.createArtifact(artifactDTO);
                } catch (IllegalArgumentException | SystemException | DuplicatedEntityException | BindException e) {
                    throw new IllegalArgumentException("cannot create artifact " + artifactName, e);
                }
                try {
                    String url = filesService.getUploadAsUrl(targetPath).getUrl();
                    outputs.put(path, url);
                    outputArtifacts.put(path, id);
                } catch (StoreException e) {
                    throw new IllegalArgumentException("cannot construct upload url for artifact " + artifactName);
                }
            }
        }

        HPCDLRunnable runnable = HPCDLRunnable
            .builder()
            .runtime(HPCDLRuntime.RUNTIME)
            .task(HPCDLJobTaskSpec.KIND)
            .state(State.READY.name())
            //base
            .image(functionSpec.getImage())
            .command(functionSpec.getCommand())
            .args(runSpec.getArgs() != null ? runSpec.getArgs().toArray(new String[0]) : null)
            //specific
            .outputs(outputs)
            .inputs(inputs)
            .outputArtifacts(outputArtifacts)
            .build();
        runnable.setId(run.getId());
        runnable.setProject(run.getProject());

        return runnable;
    }

    private String generateKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
