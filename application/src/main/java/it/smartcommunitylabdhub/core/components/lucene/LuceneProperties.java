package it.smartcommunitylabdhub.core.components.lucene;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@NoArgsConstructor
@ToString
@ConfigurationProperties(prefix = "lucene", ignoreUnknownFields = true)
public class LuceneProperties {
    private String indexPath;
    private String reindex;
}
