package it.smartcommunitylabdhub.runtime.kubeai.models;

public enum KubeAIEngine {

    OLLAMA("OLlama"),
    VLLM("VLLM"),
    FASTERWHISPER("FasterWhisper"),
    INFINITY("Infinity"),;

    private final String engine;

    KubeAIEngine(String engine) {
        this.engine = engine;
    }

    public String getEngine() {
        return engine;
    }
}
