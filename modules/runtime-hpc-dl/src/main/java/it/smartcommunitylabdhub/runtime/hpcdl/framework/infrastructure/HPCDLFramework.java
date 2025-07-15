package it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.exceptions.FrameworkException;
import it.smartcommunitylabdhub.commons.infrastructure.Framework;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.exceptions.HPCDLFrameworkException;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.objects.HPCDLJob;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.runnables.HPCDLRunnable;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

@Slf4j
@FrameworkComponent(framework = HPCDLFramework.FRAMEWORK)
public class HPCDLFramework implements Framework<HPCDLRunnable>, InitializingBean {


    public static final String FRAMEWORK = "hpcdljob";

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    protected static final ObjectMapper mapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;

    private HPCDLConnector connector;

    @Autowired
    public void setHPCDLConnector(HPCDLConnector connector) {
        this.connector = connector;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(connector, "HPCDLConnector is required");
    }

    @Override
    public HPCDLRunnable run(HPCDLRunnable runnable) throws HPCDLFrameworkException {
        log.info("run for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }


        //build job
        HPCDLJob job = build(runnable);

        //create job
        job = create(job);

        
        Map<String, Serializable> results = new HashMap<>();
        results.put("job", job);

        runnable.setJob(job);

        //update state
        if (State.ERROR.name().equals(job.getStatus())) {
            runnable.setState(job.getStatus());
        } else {
            runnable.setState(State.RUNNING.name());
        }

        try {
            runnable.setResults(
                results
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Entry::getKey, e -> mapper.convertValue(e.getValue(), typeRef)))
            );
        } catch (IllegalArgumentException e) {
            log.error("error reading HPC-DL job results: {}", e.getMessage());
        }

        if (job != null) {
            runnable.setMessage(String.format("job %s created", job.getId()));
        }

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public HPCDLRunnable stop(HPCDLRunnable runnable) throws HPCDLFrameworkException {
        log.info("stop for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        List<String> messages = new ArrayList<>();

        HPCDLJob job = get(build(runnable));
        //stop by deleting
        log.info("stopping HPC-DL job for {}", String.valueOf(runnable.getId()));
        delete(job);
        messages.add(String.format("job %s deleted", job.getId()));

        //update state
        runnable.setState(State.STOPPED.name());

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public HPCDLRunnable delete(HPCDLRunnable runnable) throws HPCDLFrameworkException {
        log.info("delete for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }


        List<String> messages = new ArrayList<>();

        HPCDLJob job;
        try {
            job = get(build(runnable));
        } catch (HPCDLFrameworkException e) {
            runnable.setState(State.DELETED.name());
            return runnable;
        }
        //stop by deleting
        log.info("delete HPC-DL job for {}", runnable.getId());
        delete(job);
        messages.add(String.format("job %s deleted", job.getId()));

        //update state
        runnable.setState(State.DELETED.name());

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public HPCDLRunnable resume(HPCDLRunnable runnable) throws FrameworkException {
        throw new UnsupportedOperationException("Unimplemented method 'resume'");
    }

    /**
     * Build Argo Workflow CR. Populate template defaults and artifact repository reference.
     * @param runnable
     * @param secret
     * @return
     * @throws K8sArgoFrameworkException
     */
    public HPCDLJob build(HPCDLRunnable runnable) throws HPCDLFrameworkException {
        log.debug("build for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        HPCDLJob job = new HPCDLJob();

        if (runnable.getJob() != null) {
            job = runnable.getJob();
        } else {
            job.setImage(runnable.getImage());
            job.setInputs(runnable.getInputs());
            job.setOutputs(runnable.getOutputs());
            job.setArgs(runnable.getArgs());
            job.setCommand(runnable.getCommand());
            job.setConfig(runnable.getConfig());
        }
        return job;
    }


    /*
     * HPC DL integration
     */
    public HPCDLJob apply(@NotNull HPCDLJob job) throws HPCDLFrameworkException {
        return job;
    }

    public HPCDLJob get(@NotNull HPCDLJob job) throws HPCDLFrameworkException {

        if (State.ERROR.name().equals(job.getStatus()) || State.STOPPED.name().equals(job.getStatus()) || State.DELETED.name().equals(job.getStatus()) | State.COMPLETED.name().equals(job.getStatus())) {
            // if job is in error or stopped or deleted or completed, we do not need to fetch it
            log.debug("HPC-DL job {} is in state {}, no need to fetch it", job.getId(), job.getStatus());
            return job;
        }

        try {
            String id = job.getId();
            log.debug("get HPC-DL job for {}", id);
            HPCDLJob remoteJob = connector.getJob(id, job.getHpcIds());
            if (remoteJob != null) {
                job.setHpcIds(remoteJob.getHpcIds());
                job.setStatus(remoteJob.getStatus());
                if (remoteJob.getMessage() != null) {
                    job.setMessage(remoteJob.getMessage());
                }
                if (remoteJob.getMetrics() != null && !remoteJob.getMetrics().isEmpty()) {
                    job.setMetrics(remoteJob.getMetrics());
                }
            }

            return job;
        } catch (Exception e) {
            log.error("Error with HPC-DL API: {}", e.getMessage());
            return job;
            // throw new HPCDLFrameworkException(e.getMessage(), e);
        }
    }

    public HPCDLJob create(HPCDLJob job) throws HPCDLFrameworkException {

        try {
            log.debug("create HPC-DL job for {}", job.getId());
            return connector.createJob(job);
        } catch (Exception e) {
            log.error("Error with  HPC-DL API: {}", e);
            throw new HPCDLFrameworkException(e.getMessage(), e);
        }
    }

    public void delete(HPCDLJob job) throws HPCDLFrameworkException {

        try {
            String id = job.getId();
            connector.stopJob(id);
            log.debug("delete HPC-DL job for {}", id);
        } catch (Exception e) {
            log.error("Error with  HPC-DL API: {}", e);
            throw new HPCDLFrameworkException(e.getMessage(), e);
        }
    }



}
