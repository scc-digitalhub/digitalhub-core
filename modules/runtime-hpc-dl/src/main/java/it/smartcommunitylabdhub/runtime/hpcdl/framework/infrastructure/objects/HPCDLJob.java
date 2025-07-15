package it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.objects;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class HPCDLJob implements Serializable{

    private String id;
    private Collection<String> hpcIds;
    private String image;
    private String[] args;
    private String command;

    private Map<String, Serializable> config = new HashMap<>();

    private Map<String, String> inputs = new HashMap<>();
    private Map<String, String> outputs = new HashMap<>();

    private String status;
    private String message;

    private Map<String, String> metrics = new HashMap<>();

    public HPCDLJob(HPCDLJob job) {
        this.id = job.getId();
        this.hpcIds = job.getHpcIds();
        this.image = job.getImage();
        this.args = job.getArgs();
        this.inputs = job.getInputs();
        this.outputs = job.getOutputs();
        this.status = job.getStatus();
        this.message = job.getMessage();
        this.command = job.getCommand();
        this.metrics = job.getMetrics();
        this.config = job.getConfig();
    }

}
