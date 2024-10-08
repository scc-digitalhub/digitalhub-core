package it.smartcommunitylabdhub.core.config;

import it.smartcommunitylabdhub.core.components.validators.ValidatorFactory;
import it.smartcommunitylabdhub.core.models.validators.interfaces.BaseValidator;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(7)
public class ValidatorConfig {

    @Bean
    protected ValidatorFactory validatorFactory(List<? extends BaseValidator> frameworks) {
        return new ValidatorFactory(frameworks);
    }
}
