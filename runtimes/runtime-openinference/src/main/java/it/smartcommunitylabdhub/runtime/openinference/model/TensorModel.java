package it.smartcommunitylabdhub.runtime.openinference.model;

import java.util.List;

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
    private TensorDatatype datatype;
    private List<Integer> shape;
}
