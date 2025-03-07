package it.smartcommunitylabdhub.core.components.solr;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import it.smartcommunitylabdhub.core.models.indexers.EntityIndexer;
import it.smartcommunitylabdhub.core.models.indexers.IndexField;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@Profile("generate-solr")
public class SolrFieldsExportRunner  implements CommandLineRunner {
    @Autowired
    ApplicationContext context;
    
    @Autowired
    private TemplateEngine templateEngine;
    
    private List<EntityIndexer<?>> services;
    
    @Autowired(required = false)
    public void setServices(List<EntityIndexer<?>> services) {
        this.services = services;
    }

    @Override
	public void run(String... args) throws Exception {
		log.info("Running solr fields export...");
		int returnCode = 0;
		if(services != null) {
			try {
				Map<String, IndexField> fieldsMap = new HashMap<>();
				services.forEach(service -> {
					for(IndexField field : service.fields()) {
						if(!fieldsMap.containsKey(field.getName()))
							fieldsMap.put(field.getName(), field);
					}
				});
				Map<String, Object> variables = new HashMap<>();
				variables.put("fields", fieldsMap.values());
		        final Context ctx = new Context();
		        ctx.setVariables(variables);
		        String content = templateEngine.process("solr_fields.xml", ctx);
		        String out = "solr/solr_fields.xml";
		        Path fp = Paths.get(out);
		        Files.createDirectories(fp.getParent());
		        File file = new File(out);
		        log.info("writing solr fields to {}...",file.getAbsolutePath());
		        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
			} catch (Exception e) {
				log.error("Error with export: {}", e.getMessage());
				returnCode = 1;
			}
		}
        int exitCode = returnCode == 0
                ? SpringApplication.exit(context, () -> 0)
                : SpringApplication.exit(context, () -> 1);
        System.exit(exitCode);		
	}
}
