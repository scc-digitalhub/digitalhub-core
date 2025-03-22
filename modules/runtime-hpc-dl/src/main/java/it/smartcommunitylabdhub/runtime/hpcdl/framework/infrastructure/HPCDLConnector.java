package it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure;

import it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.objects.HPCDLJob;

public interface HPCDLConnector {

    public HPCDLJob createJob(HPCDLJob job);

    public HPCDLJob getJob(String jobId);

    public HPCDLJob stopJob(String jobId);
}
