package it.smartcommunitylabdhub.commons.models.base;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "baseBuilder")
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseMetadata extends BaseSpec {

    protected String project;

    protected String name;
    protected String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-ddTHH:mm:ss.SSSZ")
    protected LocalDateTime created;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-ddTHH:mm:ss.SSSZ")
    protected LocalDateTime updated;

    protected Set<String> labels;

    @Override
    public void configure(Map<String, Serializable> data) {
        BaseMetadata meta = mapper.convertValue(data, BaseMetadata.class);

        this.project = meta.getProject();

        this.name = meta.getName();
        this.description = meta.getDescription();

        this.created = meta.getCreated();
        this.updated = meta.getUpdated();

        this.labels = meta.getLabels();
    }
}
