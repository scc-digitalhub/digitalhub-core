package it.smartcommunitylabdhub.runtime.openinference.model;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TensorModel {

    private String name;
    @NotNull
    private TensorDatatype datatype;
    @NotEmpty
    private List<Integer> shape;
}
