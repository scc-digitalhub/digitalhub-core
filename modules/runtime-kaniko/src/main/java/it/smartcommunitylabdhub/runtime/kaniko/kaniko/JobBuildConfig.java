package it.smartcommunitylabdhub.runtime.kaniko.kaniko;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobBuildConfig {

    private String type;
    private String uuid;
    private String name;

    public String getIdentifier() {
        return "-" + type + "-" + uuid;
    }
}
