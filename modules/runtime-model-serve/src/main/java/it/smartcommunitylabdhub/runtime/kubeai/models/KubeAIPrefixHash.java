package it.smartcommunitylabdhub.runtime.kubeai.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KubeAIPrefixHash {

    private Integer meanLoadFactor;
    private Integer replication;
    private Integer prefixCharLength;
}
