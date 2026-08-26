package it.smartcommunitylabdhub.projects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import org.springframework.lang.Nullable;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import it.smartcommunitylabdhub.commons.models.project.BaseProject;
import it.smartcommunitylabdhub.extensions.model.ExtensibleDTO;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@SuperBuilder
public class Project extends BaseProject implements ExtensibleDTO {

    @Nullable
    @Builder.Default
    private List<Map<String, Serializable>> extensions = new LinkedList<>();

}
