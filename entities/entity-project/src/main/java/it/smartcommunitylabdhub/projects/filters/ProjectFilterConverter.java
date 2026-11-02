package it.smartcommunitylabdhub.projects.filters;

import it.smartcommunitylabdhub.authorization.model.ResourceShareEntity;
import it.smartcommunitylabdhub.authorization.services.ResourceSharingService;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.queries.SearchCriteria;
import it.smartcommunitylabdhub.commons.models.queries.SearchCriteria.Operation;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.core.queries.filters.AbstractEntityFilterConverter;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntityFilter;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntitySearchCriteria;
import it.smartcommunitylabdhub.projects.Project;
import it.smartcommunitylabdhub.projects.persistence.ProjectEntity;
import it.smartcommunitylabdhub.projects.persistence.ProjectEntity_;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProjectFilterConverter extends AbstractEntityFilterConverter<Project, ProjectEntity> {

    @Autowired
    private ResourceSharingService sharingService;

    @Override
    public SearchFilter<ProjectEntity> convert(@NonNull SearchFilter<Project> filter) {
        //custom handle createdBy:shared

        if (filter.getCriteria() != null) {
            List<SearchCriteria<Project>> criteria = filter
                .getCriteria()
                .stream()
                .map(c -> {
                    log.trace("Processing search criteria: {}", c);

                    if (
                        ProjectEntity_.CREATED_BY.equals(c.getField()) &&
                        (c.getValue() instanceof String v) &&
                        v.endsWith(":shared")
                    ) {
                        String user = v.substring(0, v.indexOf(":shared"));

                        try {
                            //clear createdBy and replace with id in
                            List<String> sharedWithMe = sharingService
                                .listByUser(user)
                                .stream()
                                .filter(s -> EntityUtils.getEntityName(Project.class).equalsIgnoreCase(s.getEntity()))
                                .map(ResourceShareEntity::getEntityId)
                                .toList();

                            List<String> sharedWithEveryone = sharingService
                                .listByUser("*")
                                .stream()
                                .filter(s -> EntityUtils.getEntityName(Project.class).equalsIgnoreCase(s.getEntity()))
                                .map(ResourceShareEntity::getEntityId)
                                .toList();

                            Set<String> shares = new HashSet<>();
                            shares.addAll(sharedWithMe);
                            shares.addAll(sharedWithEveryone);

                            BaseEntitySearchCriteria<Project> sc = BaseEntitySearchCriteria.<Project>builder()
                                .field(ProjectEntity_.ID)
                                .value(new ArrayList<>(shares))
                                .operation(Operation.in)
                                .build();

                            if (log.isTraceEnabled()) {
                                log.trace("Derived criteria: {}", sc);
                            }

                            return sc;
                        } catch (StoreException e) {
                            //clear shared to avoid filtering by it
                            BaseEntitySearchCriteria<Project> sc = BaseEntitySearchCriteria.<Project>builder()
                                .field(ProjectEntity_.CREATED_BY)
                                .value(user)
                                .operation(Operation.equal)
                                .build();

                            if (log.isTraceEnabled()) {
                                log.trace("Derived criteria: {}", sc);
                            }

                            return sc;
                        }
                    } else {
                        return c;
                    }
                })
                .toList();

            //rebuild filter
            BaseEntityFilter<Project> sf = BaseEntityFilter.<Project>builder()
                .criteria(criteria)
                .condition(filter.getCondition())
                .filters(filter.getFilters())
                .build();

            return super.convert(sf);
        }

        return super.convert(filter);
    }
}
