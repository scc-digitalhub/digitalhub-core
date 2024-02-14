package it.smartcommunitylabdhub.commons.models.entities.workflow.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.commons.models.base.BaseMetadata;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowMetadata extends BaseMetadata {

    String name;

    String version;

    String description;

    Boolean embedded;
}