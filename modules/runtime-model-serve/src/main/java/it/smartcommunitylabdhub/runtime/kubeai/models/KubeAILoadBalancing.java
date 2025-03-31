package it.smartcommunitylabdhub.runtime.kubeai.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KubeAILoadBalancing {
    
    @Schema(title = "fields.kubeai.strategy.title", description = "fields.kubeai.strategy.description", defaultValue = "LeastLoad")
    @Builder.Default
    private KubeAILoadBalancingStrategy strategy = KubeAILoadBalancingStrategy.LeastLoad;
    @Schema(title = "fields.kubeai.prefixhash.title", description = "fields.kubeai.prefixhash.description")
    private KubeAIPrefixHash prefixHash;
}
