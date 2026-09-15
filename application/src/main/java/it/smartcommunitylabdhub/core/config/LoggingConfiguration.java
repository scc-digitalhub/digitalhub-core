package it.smartcommunitylabdhub.core.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import it.smartcommunitylabdhub.core.components.logs.InMemoryLogAppender;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingConfiguration {

    @Bean
    InMemoryLogAppender inMemoryLogAppender() {
        return new InMemoryLogAppender(1000);
    }

    @Bean
    ApplicationRunner registerLogAppender(InMemoryLogAppender appender) {
        return args -> {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            appender.setContext(context);
            appender.start();

            context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        };
    }
}
