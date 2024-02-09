package it.smartcommunitylabdhub.core.models.builders.function;

import it.smartcommunitylabdhub.commons.infrastructure.enums.EntityName;
import it.smartcommunitylabdhub.commons.infrastructure.factories.specs.SpecRegistry;
import it.smartcommunitylabdhub.commons.models.accessors.fields.FunctionFieldAccessor;
import it.smartcommunitylabdhub.commons.models.entities.function.Function;
import it.smartcommunitylabdhub.commons.models.entities.function.specs.FunctionBaseSpec;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.utils.jackson.JacksonMapper;
import it.smartcommunitylabdhub.core.models.builders.EntityFactory;
import it.smartcommunitylabdhub.core.models.converters.ConversionUtils;
import it.smartcommunitylabdhub.core.models.entities.function.FunctionEntity;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FunctionEntityBuilder {

  @Autowired
  SpecRegistry specRegistry;

  /**
   * Build a function from a functionDTO and store extra values as f cbor
   * <p>
   *
   * @param functionDTO the functionDTO that need to be stored
   * @return Function
   */
  public FunctionEntity build(Function functionDTO) {
    // Validate spec
    specRegistry.createSpec(
      functionDTO.getKind(),
      EntityName.FUNCTION,
      Map.of()
    );

    // Retrieve field accessor
    FunctionFieldAccessor functionFieldAccessor = FunctionFieldAccessor.with(
      JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
        functionDTO,
        JacksonMapper.typeRef
      )
    );

    // Retrieve Spec
    FunctionBaseSpec spec = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
      functionDTO.getSpec(),
      FunctionBaseSpec.class
    );

    return EntityFactory.combine(
      FunctionEntity.builder().build(),
      functionDTO,
      builder ->
        builder
          // check id
          .withIf(
            functionDTO.getId() != null,
            f -> f.setId(functionDTO.getId())
          )
          .with(f -> f.setName(functionDTO.getName()))
          .with(f -> f.setKind(functionDTO.getKind()))
          .with(f -> f.setProject(functionDTO.getProject()))
          .with(f ->
            f.setMetadata(
              ConversionUtils.convert(functionDTO.getMetadata(), "metadata")
            )
          )
          .with(f ->
            f.setExtra(ConversionUtils.convert(functionDTO.getExtra(), "cbor"))
          )
          .with(f -> f.setSpec(ConversionUtils.convert(spec.toMap(), "cbor")))
          .with(f ->
            f.setStatus(
              ConversionUtils.convert(functionDTO.getStatus(), "cbor")
            )
          )
          // Store status if not present
          .withIfElse(
            functionFieldAccessor.getState().equals(State.NONE.name()),
            (f, condition) -> {
              if (condition) {
                f.setState(State.CREATED);
              } else {
                f.setState(State.valueOf(functionFieldAccessor.getState()));
              }
            }
          )
          // Metadata Extraction
          .withIfElse(
            functionDTO.getMetadata().getEmbedded() == null,
            (f, condition) -> {
              if (condition) {
                f.setEmbedded(false);
              } else {
                f.setEmbedded(functionDTO.getMetadata().getEmbedded());
              }
            }
          )
          .withIf(
            functionDTO.getMetadata().getCreated() != null,
            f -> f.setCreated(functionDTO.getMetadata().getCreated())
          )
          .withIf(
            functionDTO.getMetadata().getUpdated() != null,
            f -> f.setUpdated(functionDTO.getMetadata().getUpdated())
          )
    );
  }

  /**
   * Update a function if element is not passed it override causing empty field
   *
   * @param function the function to update
   * @return Function
   */
  public FunctionEntity update(FunctionEntity function, Function functionDTO) {
    FunctionEntity newFunction = build(functionDTO);
    return doUpdate(function, newFunction);
  }

  private FunctionEntity doUpdate(
    FunctionEntity function,
    FunctionEntity newFunction
  ) {
    return EntityFactory.combine(
      function,
      newFunction,
      builder ->
        builder
          .withIfElse(
            newFunction.getState().name().equals(State.NONE.name()),
            (f, condition) -> {
              if (condition) {
                f.setState(State.CREATED);
              } else {
                f.setState(newFunction.getState());
              }
            }
          )
          .with(f -> f.setMetadata(newFunction.getMetadata()))
          .with(f -> f.setExtra(newFunction.getExtra()))
          .with(f -> f.setStatus(newFunction.getStatus()))
          // Metadata Extraction
          .withIfElse(
            newFunction.getEmbedded() == null,
            (f, condition) -> {
              if (condition) {
                f.setEmbedded(false);
              } else {
                f.setEmbedded(newFunction.getEmbedded());
              }
            }
          )
    );
  }
}
