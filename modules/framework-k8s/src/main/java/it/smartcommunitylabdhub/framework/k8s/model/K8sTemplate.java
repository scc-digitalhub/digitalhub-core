package it.smartcommunitylabdhub.framework.k8s.model;

import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Service;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class K8sTemplate<T extends K8sRunnable> {

    private String name;
    private String description;

    @NotNull
    private T profile;

    private V1Job job;
    private V1Deployment deployment;
    private V1Service service;
    private V1CronJob cronJob;
}
