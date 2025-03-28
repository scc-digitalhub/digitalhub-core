package it.smartcommunitylabdhub.runtime.kubeai.models;

public enum KubeAIFeature {
    TEXT_GENERATION("TextGeneration"),
    TEXT_EMBEDDING("TextEmbedding"),
    SPEECH_TO_TEXT("SpeechToText");

    private final String feature;

    KubeAIFeature(String feautre) {
        this.feature = feautre;
    }

    public String getFeature() {
        return feature;
    }
}
