package it.smartcommunitylabdhub.framework.k8s.objects;

import java.io.Serializable;
import java.util.List;
import lombok.*;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CoreContainer implements Serializable {

    String name;
    String image;
    List<String> command;
    List<String> args;
    String workingDir;
    List<CoreEnv> envs;
    List<CoreResource> resources;
    List<CoreVolume> volumes;
    List<CoreLabel> labels;


}
