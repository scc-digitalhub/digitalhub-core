package it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure;

import java.util.Collection;

import it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.objects.HPCDLJob;

public interface HPCDLConnector {

    public HPCDLJob createJob(HPCDLJob job);

    public HPCDLJob getJob(String jobId, Collection<String> hpcIds);

    public HPCDLJob stopJob(String jobId);
}
