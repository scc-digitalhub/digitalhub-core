package it.smartcommunitylabdhub.security.service;
import org.springframework.util.Assert;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.stereotype.Component;
import it.smartcommunitylabdhub.commons.models.template.Template;
import it.smartcommunitylabdhub.triggers.Trigger;
import it.smartcommunitylabdhub.templates.TemplateProvider;

@Component
@Slf4j
public class TriggerTemplateProvider implements InitializingBean, TemplateProvider<Trigger> {

    private final SecurityAnalyzerRegistry analyzerRegistry;
    private List <Template> templates;

    private StringKeyGenerator keyGenerator = () -> UUID.randomUUID().toString().replace("-", "");


    public TriggerTemplateProvider(SecurityAnalyzerRegistry analyzerRegistry) {
        Assert.notNull(analyzerRegistry, "analyzer registry is required");
        this.analyzerRegistry = analyzerRegistry;
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {

        templates = analyzerRegistry.list().stream().filter(analyzer -> analyzer.getTrigger() != null).map(analyzer -> {
            return Template.builder()
                .id(keyGenerator.generateKey())
                .name(analyzer.getName())
                .kind(analyzer.getTrigger().getKind())
                .metadata(analyzer.getTrigger().getMetadata())
                .spec(new HashMap<>(analyzer.getTrigger().getSpec()))
                .build();
        }).toList();
        log.info("loaded {} templates", templates.size());
    }

    @Override
    public List<Template> getTemplates() {
        return templates;
    }
}
