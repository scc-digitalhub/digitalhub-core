package it.smartcommunitylabdhub.runtime.hpcdl.runners;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.artifact.ArtifactBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.ArtifactManager;
import it.smartcommunitylabdhub.commons.services.ProjectManager;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.service.FilesService;
import it.smartcommunitylabdhub.runtime.hpcdl.HPCDLRuntime;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.runnables.HPCDLRunnable;
import it.smartcommunitylabdhub.runtime.hpcdl.specs.HPCDLFunctionSpec;
import it.smartcommunitylabdhub.runtime.hpcdl.specs.HPCDLJobTaskSpec;
import it.smartcommunitylabdhub.runtime.hpcdl.specs.HPCDLRunSpec;

public class HPCDLJobRunner {

    private FilesService filesService;
    private ArtifactManager artifactManager;
    private ProjectManager projectManager;
    private CredentialsService credentialsService;

    public HPCDLJobRunner(FilesService filesService, ArtifactManager artifactManager, ProjectManager projectManager, CredentialsService credentialsService) {
        this.filesService = filesService;
        this.artifactManager = artifactManager;
        this.projectManager = projectManager;
        this.credentialsService = credentialsService;
    }

    public HPCDLRunnable produce(Run run) {
        HPCDLRunSpec runSpec = new HPCDLRunSpec(run.getSpec());
        HPCDLFunctionSpec functionSpec = runSpec.getFunctionSpec();
        if (functionSpec == null) {
            throw new IllegalArgumentException("functionSpec is null");
        }
        Project project = projectManager.getProject(run.getProject());
                    UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        List<Credentials> credentials = auth != null && credentialsService != null
            ? credentialsService.getCredentials(auth)
            : null;



        Map<String, String> inputs = new HashMap<>();
        if (runSpec.getInputs() != null) {
            for (String path : runSpec.getInputs().keySet()) {
                // convert artifact key to download url
                String artifactKey = runSpec.getInputs().get(path);
                KeyAccessor keyAccessor = KeyAccessor.with(artifactKey);

                Artifact artifact = StringUtils.hasText(keyAccessor.getId()) && !"latest".equals(keyAccessor.getId()) 
                ? artifactManager.findArtifact(keyAccessor.getId())
                : artifactManager.getLatestArtifact(keyAccessor.getProject(), keyAccessor.getName());
                if (artifact != null) {
                    ArtifactBaseSpec spec = new ArtifactBaseSpec();
                    spec.configure(artifact.getSpec());

                    try {
                        DownloadInfo info = filesService.getDownloadAsUrl(spec.getPath(), credentials);
                        inputs.put(path, info.getUrl());
                    } catch (StoreException e) {
                        throw new IllegalArgumentException("cannot construct downoad url for artifact " + artifactKey);
                    }
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
                    filesService.getDefaultStore(project) +
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
                    artifactDTO = artifactManager.createArtifact(artifactDTO);
                } catch (IllegalArgumentException | SystemException | DuplicatedEntityException | BindException e) {
                    throw new IllegalArgumentException("cannot create artifact " + artifactName, e);
                }
                try {
                    String url = filesService.getUploadAsUrl(targetPath, credentials).getUrl();
                    outputs.put(path, url);
                    outputArtifacts.put(path, id);
                } catch (StoreException e) {
                    throw new IllegalArgumentException("cannot construct upload url for artifact " + artifactName);
                }
            }
        }

        Map<String, Serializable> conf = new HashMap<>();
        if(runSpec.getWalltime() != null) conf.put("walltime", runSpec.getWalltime());
        if(runSpec.getNodes() != null) conf.put("nodes", runSpec.getNodes());
        if(runSpec.getTasksPerNode() != null) conf.put("tasks_per_node", runSpec.getTasksPerNode());
        if(runSpec.getCpusPerTask() != null) conf.put("cpus_per_task", runSpec.getCpusPerTask());
        if(runSpec.getGpus() != null) conf.put("gpus", runSpec.getGpus());
        if(runSpec.getQos() != null) conf.put("qos", runSpec.getQos());

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
            .config(conf)
            .build();
        runnable.setId(run.getId());
        runnable.setProject(run.getProject());

        return runnable;
    }

    private String generateKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
