package it.smartcommunitylabdhub.core.models.indexers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public class ItemResult {

    String id;
    String kind;
    String project;
    String name;
    String type;
    String status;
    String key;
    Map<String, Object> metadata = new HashMap<>();
    Map<String, List<String>> highlights = new HashMap<>();

}
