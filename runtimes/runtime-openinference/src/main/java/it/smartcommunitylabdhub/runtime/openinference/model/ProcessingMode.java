package it.smartcommunitylabdhub.runtime.openinference.model;

public enum ProcessingMode {

    Preprocessor("preprocessor"),
    Postprocessor("postprocessor");

    private final String mode;

    ProcessingMode(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }
}