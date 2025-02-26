package it.smartcommunitylabdhub.core.models.indexers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class IndexField {

    private String name;
    private String type;
    private boolean indexed;
    private boolean multiValued;
    private boolean stored;
    private boolean uninvertible;
}
