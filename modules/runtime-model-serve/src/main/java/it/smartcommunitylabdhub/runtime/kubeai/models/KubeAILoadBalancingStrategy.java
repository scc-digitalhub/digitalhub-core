package it.smartcommunitylabdhub.runtime.kubeai.models;

public enum KubeAILoadBalancingStrategy {

    LEAST_LOAD("LeastLoad"),
    PREFIX_HASH("PrefixHash");


    private final String strategy;

    KubeAILoadBalancingStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getStrategy() {
        return strategy;
    }

}
