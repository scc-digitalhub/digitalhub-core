package it.smartcommunitylabdhub.core.components.lucene;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import it.smartcommunitylabdhub.core.models.indexers.IndexableEntityService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "lucene",  name = "index-path")
public class LuceneInitalizer implements ApplicationListener<ApplicationStartedEvent> {

    private final LuceneProperties properties;
	private List<IndexableEntityService<?>> services;

    public LuceneInitalizer(LuceneProperties properties) {
        Assert.notNull(properties, "lucene properties are required");
        this.properties = properties;
    }

    @Autowired(required = false)
    public void setServices(List<IndexableEntityService<?>> services) {
        this.services = services;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        // trigger reindex if required
        if (services != null && "always".equals(properties.getReindex())) {
            //reindex
            services.forEach(service -> service.reindexAll());
        }
    }
}
