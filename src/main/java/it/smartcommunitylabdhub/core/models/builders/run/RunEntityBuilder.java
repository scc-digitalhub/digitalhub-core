package it.smartcommunitylabdhub.core.models.builders.run;

import it.smartcommunitylabdhub.core.components.fsm.enums.RunState;
import it.smartcommunitylabdhub.core.components.infrastructure.enums.EntityName;
import it.smartcommunitylabdhub.core.components.infrastructure.factories.accessors.AccessorRegistry;
import it.smartcommunitylabdhub.core.components.infrastructure.factories.specs.SpecRegistry;
import it.smartcommunitylabdhub.core.exceptions.CoreException;
import it.smartcommunitylabdhub.core.models.accessors.kinds.interfaces.Accessor;
import it.smartcommunitylabdhub.core.models.accessors.kinds.interfaces.RunFieldAccessor;
import it.smartcommunitylabdhub.core.models.base.interfaces.Spec;
import it.smartcommunitylabdhub.core.models.builders.EntityFactory;
import it.smartcommunitylabdhub.core.models.converters.ConversionUtils;
import it.smartcommunitylabdhub.core.models.entities.run.Run;
import it.smartcommunitylabdhub.core.models.entities.run.RunEntity;
import it.smartcommunitylabdhub.core.models.entities.run.specs.RunBaseSpec;
import it.smartcommunitylabdhub.core.models.enums.State;
import it.smartcommunitylabdhub.core.utils.ErrorList;
import it.smartcommunitylabdhub.core.utils.jackson.JacksonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

@Component
public class RunEntityBuilder {


    @Autowired
    SpecRegistry<? extends Spec> specRegistry;

    @Autowired
    AccessorRegistry<? extends Accessor<Object>> accessorRegistry;


    /**
     * Build a Run from a RunDTO and store extra values as a cbor
     *
     * @param runDTO the run dto
     * @return Run
     */
    public RunEntity build(Run runDTO) {

        // Validate Spec
        specRegistry.createSpec(runDTO.getKind(), EntityName.RUN, Map.of());

        // Retrieve Field accessor
        RunFieldAccessor<?> runFieldAccessor =
                accessorRegistry.createAccessor(
                        runDTO.getKind(),
                        EntityName.RUN,
                        JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
                                runDTO,
                                JacksonMapper.typeRef));

        // Retrieve base spec
        RunBaseSpec spec = JacksonMapper.CUSTOM_OBJECT_MAPPER
                .convertValue(runDTO.getSpec(), RunBaseSpec.class);


        return EntityFactory.combine(
                RunEntity.builder().build(), runDTO, builder -> builder
                        // check id
                        .withIf(runDTO.getId() != null,
                                (r) -> r.setId(runDTO.getId()))
                        .with(r -> r.setProject(runDTO.getProject()))
                        .with(r -> r.setKind(runDTO.getKind()))
                        .with(r -> r.setTask(Optional.ofNullable(
                                StringUtils.hasText(spec.getTask()) ? spec.getTask() : null
                        ).orElseThrow(() -> new CoreException(
                                ErrorList.TASK_NOT_FOUND.getValue(),
                                ErrorList.TASK_NOT_FOUND.getReason(),
                                HttpStatus.INTERNAL_SERVER_ERROR
                        ))))
                        .with(r -> r.setTaskId(spec.getTaskId()))
                        .withIfElse(runFieldAccessor.getState().equals(State.NONE.name()),
                                (r, condition) -> {
                                    if (condition) {
                                        r.setState(RunState.CREATED);
                                    } else {
                                        r.setState(RunState.valueOf(runFieldAccessor.getState()));
                                    }
                                }
                        )
                        .with(r -> r.setMetadata(ConversionUtils.convert(
                                runDTO.getMetadata(), "metadata")))
                        .with(r -> r.setExtra(ConversionUtils.convert(
                                runDTO.getExtra(), "cbor")))
                        .with(r -> r.setSpec(ConversionUtils.convert(
                                spec.toMap(), "cbor")))
                        .with(r -> r.setStatus(ConversionUtils.convert(
                                runDTO.getStatus(), "cbor")))
                        .withIf(runDTO.getMetadata().getCreated() != null, (r) ->
                                r.setCreated(runDTO.getMetadata().getCreated()))
                        .withIf(runDTO.getMetadata().getUpdated() != null, (r) ->
                                r.setUpdated(runDTO.getMetadata().getUpdated())
                        )
        );

    }

    /**
     * Update a Run if element is not passed it override causing empty field
     *
     * @param run    the Run
     * @param runDTO the run DTO
     * @return Run
     */
    public RunEntity update(RunEntity run, Run runDTO) {

        RunEntity newRun = build(runDTO);
        return doUpdate(run, newRun);
    }

    /**
     * Updates the given RunEntity with the properties of the newRun entity.
     *
     * @param run    the original RunEntity to be updated
     * @param newRun the new RunEntity containing the updated properties
     * @return the updated RunEntity after the merge operation
     */
    public RunEntity doUpdate(RunEntity run, RunEntity newRun) {

        return EntityFactory.combine(
                run, newRun, builder -> builder
                        .withIfElse(newRun.getState().name().equals(State.NONE.name()),
                                (r, condition) -> {
                                    if (condition) {
                                        r.setState(RunState.CREATED);
                                    } else {
                                        r.setState(newRun.getState());
                                    }
                                }
                        )
                        .with(r -> r.setStatus(newRun.getStatus()))
                        .with(p -> p.setMetadata(newRun.getMetadata())));
    }
}
