package it.smartcommunitylabdhub.core.models.accessors.kinds.projects;

import it.smartcommunitylabdhub.core.annotations.common.AccessorType;
import it.smartcommunitylabdhub.core.components.infrastructure.enums.EntityName;
import it.smartcommunitylabdhub.core.models.accessors.AbstractFieldAccessor;
import it.smartcommunitylabdhub.core.models.accessors.kinds.interfaces.ProjectFieldAccessor;


@AccessorType(kind = "project", entity = EntityName.PROJECT, factory = ProjectDefaultFieldAccessor.class)
public class ProjectDefaultFieldAccessor
        extends AbstractFieldAccessor<ProjectDefaultFieldAccessor>
        implements ProjectFieldAccessor<ProjectDefaultFieldAccessor> {
}
