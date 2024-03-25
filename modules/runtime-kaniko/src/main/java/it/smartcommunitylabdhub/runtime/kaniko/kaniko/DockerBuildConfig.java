package it.smartcommunitylabdhub.runtime.kaniko.kaniko;

import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DockerBuildConfig {

    private String dockerTemplatePath;
    private String dockerTargetPath;
    private String baseImage;
    private String entrypointCommand;
    private String sharedData;
    private String dirData;

    @Builder.Default
    private List<String> additionalCommands = new ArrayList<>();

    public DockerBuildConfig addCommand(String value) {
        additionalCommands.add(value);
        return this;
    }
}
