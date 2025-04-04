package it.smartcommunitylabdhub.runtime.kubeai.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KubeAIFile {

    @Schema(title = "fields.kubeai.filepath.title", description = "fields.kubeai.filepath.description")
    private String path;
    @Schema(title = "fields.kubeai.filecontent.title", description = "fields.kubeai.filecontent.description")
    private String content;
}
