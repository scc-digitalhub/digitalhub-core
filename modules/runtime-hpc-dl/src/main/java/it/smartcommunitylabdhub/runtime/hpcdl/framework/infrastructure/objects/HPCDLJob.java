package it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.objects;

import java.io.Serializable;
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
    private String image;
    private String[] args;

    private Map<String, String> inputs = new HashMap<>();
    private Map<String, String> outputs = new HashMap<>();

    private String status;
    private String message;

    public HPCDLJob(HPCDLJob job) {
        this.id = job.getId();
        this.image = job.getImage();
        this.args = job.getArgs();
        this.inputs = job.getInputs();
        this.outputs = job.getOutputs();
    }

}
