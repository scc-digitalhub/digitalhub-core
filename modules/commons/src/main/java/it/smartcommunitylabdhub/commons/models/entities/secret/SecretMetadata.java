package it.smartcommunitylabdhub.commons.models.entities.secret;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.commons.models.base.BaseMetadata;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecretMetadata extends BaseMetadata {

    private String version;
    private Boolean embedded;

    @Builder
    public SecretMetadata(
        String project,
        String name,
        String description,
        LocalDateTime created,
        LocalDateTime updated,
        Set<String> labels,
        String version,
        Boolean embedded
    ) {
        super(project, name, description, created, updated, labels);
        this.version = version;
        this.embedded = embedded;
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        SecretMetadata meta = mapper.convertValue(data, SecretMetadata.class);
        this.version = meta.getVersion();
        this.embedded = meta.getEmbedded();
    }
}
