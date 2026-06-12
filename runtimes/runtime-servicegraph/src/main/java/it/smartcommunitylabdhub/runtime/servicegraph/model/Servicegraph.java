package it.smartcommunitylabdhub.runtime.servicegraph.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import it.smartcommunitylabdhub.commons.jackson.YamlMapperFactory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Servicegraph {

    private static final YAMLFactory YAML_FACTORY = YamlMapperFactory.yamlFactory();
    private static final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(YAML_FACTORY);


    private Input input;
    private Node flow;
    private Output output;
    
    public String ground() {
        // write the object as yaml string
        try {
            return YAML_OBJECT_MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Error grounding servicegraph", e);
        }
    }
   
}
