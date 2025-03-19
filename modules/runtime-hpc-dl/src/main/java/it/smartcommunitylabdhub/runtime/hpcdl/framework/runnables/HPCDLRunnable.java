package it.smartcommunitylabdhub.runtime.hpcdl.framework.runnables;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RunnableComponent;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.HPCDLFramework;

import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@RunnableComponent(framework = HPCDLFramework.FRAMEWORK)
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HPCDLRunnable implements RunRunnable  {

    private String id;

    private String project;

    private String runtime;
    
    private String user;

    private String task;

    private String image;

    private String command;

    private String[] args;

    private Map<String, String> inputs;

    private Map<String, String> outputs;

    private Map<String, String> outputArtifacts;

    private String state;

    private String error;

    private String message;

    private Map<String, Serializable> results;

    @Override
    public String getFramework() {
        return HPCDLFramework.FRAMEWORK;
    }

}
