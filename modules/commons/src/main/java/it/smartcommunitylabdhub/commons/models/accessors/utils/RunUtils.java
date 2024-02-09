package it.smartcommunitylabdhub.commons.models.accessors.utils;

import it.smartcommunitylabdhub.commons.exceptions.CoreException;
import it.smartcommunitylabdhub.commons.models.accessors.entities.RunAccessor;
import it.smartcommunitylabdhub.commons.models.base.BaseEntity;
import it.smartcommunitylabdhub.commons.models.entities.function.Function;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import it.smartcommunitylabdhub.commons.models.entities.workflow.Workflow;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

//TODO remove, this goes into the accessor
public class RunUtils {

    private static final Pattern RUN_PATTERN = Pattern.compile("([^:/]+)\\+([^+]+)://([^/]+)/([^/]+):(.+)");

    private RunUtils() {}

    public static RunAccessor parseRun(String RunString) {
        Matcher matcher = RUN_PATTERN.matcher(RunString);
        if (matcher.matches()) {
            String kind = matcher.group(1);
            String perform = matcher.group(2);
            String project = matcher.group(3);
            String function = matcher.group(4);
            String version = matcher.group(5);

            return new RunAccessor(kind, perform, project, function, version);
        }
        throw new CoreException(
            "InvalidRunStringCase",
            "Cannot create accessor for the given Run string.",
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    public static <T extends BaseEntity> String buildRunString(T type, Task task) {
        if (type instanceof Function) {
            Function f = (Function) type;
            return (f.getKind() + "+" + task.getKind() + "://" + f.getProject() + "/" + f.getName() + ":" + f.getId());
        } else if (type instanceof Workflow) {
            Workflow w = (Workflow) type;
            return (w.getKind() + "+" + task.getKind() + "://" + w.getProject() + "/" + w.getName() + ":" + w.getId());
        } else {
            throw new CoreException(
                "CannotComposeRunField",
                "Cannot compose Run field for the given object.",
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
