package it.smartcommunitylabdhub.runtime.kfp.dtos;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class NodeStatusDTO implements Serializable {
    private String id;
    private String name;
    private String displayName;
    private String type;
    private List<String> children;
    private String state;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String exitCode;
    private List<Map<String, String>> inputs;
    private List<Map<String, String>> outputs;
    private String function;
    private String functionId;
    private String runId;
    private String action;
}
