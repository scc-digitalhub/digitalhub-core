package it.smartcommunitylabdhub.core.models.specs.report;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.report.TestBaseSpec;

@SpecType(kind = "test", entity = EntityName.REPORT)
public class TestSpec extends TestBaseSpec {}
