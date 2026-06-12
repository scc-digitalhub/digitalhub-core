package it.smartcommunitylabdhub.runtime.servicegraph.model;

public enum NodeType {

    SEQUENCE("sequence"),
    ENSEMBLE("ensemble"),
    SWITCH("switch"),
    SERVICE("service");

    private final String value;

    NodeType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
