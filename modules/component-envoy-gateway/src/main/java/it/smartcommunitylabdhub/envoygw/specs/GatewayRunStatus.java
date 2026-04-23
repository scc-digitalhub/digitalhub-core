package it.smartcommunitylabdhub.envoygw.specs;

import it.smartcommunitylabdhub.envoygw.model.GatewayInfo;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GatewayRunStatus extends RunBaseStatus {

    private GatewayInfo gatewayInfo;
    private List<K8sCRRunnable> runnables;

    @Override
    public void configure(java.util.Map<String, java.io.Serializable> data) {
        super.configure(data);

        GatewayRunStatus spec = mapper.convertValue(data, GatewayRunStatus.class);
        this.gatewayInfo = spec.getGatewayInfo();
        this.runnables = spec.getRunnables();
    }

    public static GatewayRunStatus with(java.util.Map<String, java.io.Serializable> data) {
        GatewayRunStatus spec = new GatewayRunStatus();
        spec.configure(data);
        return spec;
    }
}
