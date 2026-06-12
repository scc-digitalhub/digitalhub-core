package it.smartcommunitylabdhub.runtime.servicegraph.model;

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
public class Node {

    private NodeType type;
    private String name;
    private List<Node> nodes;
    private String condition;
    private String kind;
    private NodeSpec spec;
}
