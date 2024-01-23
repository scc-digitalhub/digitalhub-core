package it.smartcommunitylabdhub.core.models.entities.project.specs;

import it.smartcommunitylabdhub.core.models.base.specs.BaseSpec;
import it.smartcommunitylabdhub.core.utils.jackson.JacksonMapper;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Getter
@Setter
public class ProjectBaseSpec extends BaseSpec {

    String context;

    List<Object> functions = new ArrayList<>();

    List<Object> artifacts = new ArrayList<>();

    List<Object> workflows = new ArrayList<>();

    List<Object> dataitems = new ArrayList<>();


    @Override
    public void configure(Map<String, Object> data) {
        
        ProjectBaseSpec concreteSpec = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
                data, ProjectBaseSpec.class);
        this.setContext(concreteSpec.getContext());
        this.setFunctions(concreteSpec.getFunctions());
        this.setArtifacts(concreteSpec.getArtifacts());
        this.setDataitems(concreteSpec.getDataitems());
        this.setWorkflows(concreteSpec.getWorkflows());

        super.configure(data);

        this.setExtraSpecs(concreteSpec.getExtraSpecs());
    }
}
