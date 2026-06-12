package it.smartcommunitylabdhub.runtime.servicegraph.model;

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
public class Output {

    private String kind;
    private OutputSpec spec;
}
