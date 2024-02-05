package it.smartcommunitylabdhub.core.components.infrastructure.runnables;

import it.smartcommunitylabdhub.core.annotations.infrastructure.RunnableComponent;
import it.smartcommunitylabdhub.core.components.infrastructure.factories.runnables.BaseRunnable;
import lombok.*;

import java.util.Map;
import java.util.Set;


@RunnableComponent(framework = "k8sjob")
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class K8sJobRunnable extends BaseRunnable {

    String runtime;

    String task;

    String image;

    String command;

    String state;

    String[] args;

    Map<String, String> envs;

    // mapping secret name to the list of keys to of the secret to use
    Map<String, Set<String>> secrets;

    @Override
    public String getFramework() {
        return "k8sjob";
    }

}
