package it.smartcommunitylabdhub.runtime.kfp.specs;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.runtime.kfp.dtos.NodeStatusDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KFPRunStatus extends RunBaseStatus {

    private List<NodeStatusDTO> nodes;
}
