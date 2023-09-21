package it.smartcommunitylabdhub.dbt.components.kinds;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import it.smartcommunitylabdhub.core.annotations.RunBuilderComponent;
import it.smartcommunitylabdhub.core.components.kinds.factory.builders.KindBuilder;
import it.smartcommunitylabdhub.core.exceptions.CoreException;
import it.smartcommunitylabdhub.core.models.accessors.utils.RunUtils;
import it.smartcommunitylabdhub.core.models.accessors.utils.TaskAccessor;
import it.smartcommunitylabdhub.core.models.accessors.utils.TaskUtils;
import it.smartcommunitylabdhub.core.models.builders.entities.FunctionEntityBuilder;
import it.smartcommunitylabdhub.core.models.dtos.FunctionDTO;
import it.smartcommunitylabdhub.core.models.dtos.RunDTO;
import it.smartcommunitylabdhub.core.models.dtos.TaskDTO;
import it.smartcommunitylabdhub.core.repositories.TaskRepository;
import it.smartcommunitylabdhub.core.services.interfaces.FunctionService;
import it.smartcommunitylabdhub.core.utils.MapUtils;

@RunBuilderComponent(platform = "dbt", perform = "perform")
public class DbtRunBuilder implements KindBuilder<TaskDTO, RunDTO> {

	@Autowired
	TaskRepository taskRepository;

	@Autowired
	FunctionService functionService;

	@Autowired
	FunctionEntityBuilder functionEntityBuilder;

	@Override
	public RunDTO build(TaskDTO taskDTO) {

		// 1. get function if exist otherwise throw exeception.
		return taskRepository.findById(taskDTO.getId()).map(task -> {

			// 2. produce function object for DBT and put it on spec.
			TaskAccessor taskAccessor = TaskUtils.parseTask(taskDTO.getFunction());
			FunctionDTO functionDTO = functionService.getFunction(taskAccessor.getVersion());

			// 3. Merge Task spec with function spec
			// functionDTO.getSpec().putAll(taskDTO.getSpec());
			Map<String, Object> mergedSpec =
					MapUtils.mergeMaps(functionDTO.getSpec(), taskDTO.getSpec(),
							(oldValue, newValue) -> newValue);

			// 4. produce a run object and store it
			return RunDTO.builder()
					.kind("run")
					.taskId(task.getId())
					.project(task.getProject())
					.task(RunUtils.buildRunString(
							functionEntityBuilder
									.build(functionDTO),
							task))
					.spec(mergedSpec)
					.build();

		}).orElseThrow(() -> new CoreException(
				"FunctionNotFound",
				"The function you are searching for does not exist.",
				HttpStatus.NOT_FOUND));
	}

}
