package it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.objects.HPCDLJob;

// @Component
public class MockHPCDLConnector implements HPCDLConnector {

    private Map<String, HPCDLJob> jobs = new HashMap<>();
    private Map<String, Long> jobStart = new HashMap<>();

    @Override
    public HPCDLJob createJob(HPCDLJob job) {
        HPCDLJob copy = new HPCDLJob(job);
        copy.setStatus(State.RUNNING.name());
        jobs.put(copy.getId(), copy);
        jobStart.put(copy.getId(), System.currentTimeMillis());
        return copy;
    }

    @Override
    public HPCDLJob getJob(String jobId, Collection<String> hpcId) {
        HPCDLJob job = jobs.get(jobId);
        if (job != null) {
            Long running = ((System.currentTimeMillis() - jobStart.get(jobId)) / 1000);
            if (running > 30 && !State.STOPPED.name().equals(job.getStatus())) {
                job.setStatus(State.COMPLETED.name());
            }
        } else {
            job = new HPCDLJob();
            job.setId(jobId);
            job.setStatus(State.STOPPED.name());    
        }
        return job;
    }

    @Override
    public HPCDLJob stopJob(String jobId) {
        HPCDLJob job = jobs.get(jobId);
        job.setStatus(State.STOPPED.name());    
        return job;
    }

}
