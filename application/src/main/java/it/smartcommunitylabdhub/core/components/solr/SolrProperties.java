package it.smartcommunitylabdhub.core.components.solr;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@NoArgsConstructor
@ToString
@ConfigurationProperties(prefix = "solr", ignoreUnknownFields = true)
public class SolrProperties {

    private String url;
    private String collection;
    private String user;
    private String password;
    private String adminUser;
    private String adminPassword;

    private Integer timeout;
    private Integer shards;
    private Integer replicas;

    private String reindex;
}
