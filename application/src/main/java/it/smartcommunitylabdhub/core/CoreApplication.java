package it.smartcommunitylabdhub.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = { "it.smartcommunitylabdhub" })
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@ComponentScan(basePackages = { "it.smartcommunitylabdhub" })
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}
