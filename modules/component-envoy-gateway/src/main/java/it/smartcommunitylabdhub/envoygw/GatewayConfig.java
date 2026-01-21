package it.smartcommunitylabdhub.envoygw;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;

@Configuration
@PropertySource(value = "classpath:/component-envoygw.yml", factory = YamlPropertySourceFactory.class)
public class GatewayConfig {}
