package it.smartcommunitylabdhub.templates;
import java.util.List;

import it.smartcommunitylabdhub.commons.models.template.Template;
public interface TemplateProvider<D> {

    public List<Template> getTemplates();
}
