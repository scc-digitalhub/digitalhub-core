package it.smartcommunitylabdhub.core.queries.specifications;

import it.smartcommunitylabdhub.core.queries.filters.SpecificationFilter;
import jakarta.persistence.criteria.Predicate;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

public abstract class AbstractSpecificationService<T, F extends SpecificationFilter<T>> {

    protected Specification<T> createSpecification(Map<String, String> filter, F entityFilter) {
        return (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();

            // Add your custom filter based on the provided map
            predicate = entityFilter.toPredicate(root, query, criteriaBuilder);

            // Add more conditions for other filter if needed

            return predicate;
        };
    }
}
