package it.smartcommunitylabdhub.security.service;
import org.springframework.util.Assert;
import java.util.UUID;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.stereotype.Component;

import it.smartcommunitylabdhub.commons.models.template.Template;
import it.smartcommunitylabdhub.functions.Function;
import it.smartcommunitylabdhub.templates.TemplateProvider;

@Component
@Slf4j
public class FunctionTemplateProvider implements InitializingBean, TemplateProvider<Function> {

    private final SecurityAnalyzerRegistry analyzerRegistry;
    private List <Template> templates;

    private StringKeyGenerator keyGenerator = () -> UUID.randomUUID().toString().replace("-", "");

    public FunctionTemplateProvider(SecurityAnalyzerRegistry analyzerRegistry) {
        Assert.notNull(analyzerRegistry, "analyzer registry is required");
        this.analyzerRegistry = analyzerRegistry;
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {

        templates = analyzerRegistry.list().stream().filter(analyzer -> analyzer.getFunction() != null).map(analyzer -> {
            return Template.builder()
                .id(keyGenerator.generateKey())
                .name(analyzer.getName())
                .kind(analyzer.getFunction().getKind())
                .metadata(analyzer.getFunction().getMetadata())
                .spec(new HashMap<>(analyzer.getFunction().getSpec()))
                .build();
        }).toList();
        log.info("loaded {} templates", templates.size());
    }

    @Override
    public List<Template> getTemplates() {
        return templates;
    }
}
