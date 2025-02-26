package it.smartcommunitylabdhub.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import it.smartcommunitylabdhub.core.components.lucene.LuceneProperties;

@Configuration
@Order(3)
@EnableConfigurationProperties({ LuceneProperties.class })
public class LuceneConfig {}
