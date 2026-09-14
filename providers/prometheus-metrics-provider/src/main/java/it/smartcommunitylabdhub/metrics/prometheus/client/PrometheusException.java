package it.smartcommunitylabdhub.metrics.prometheus.client;

public class PrometheusException extends RuntimeException {

    private final int statusCode;

    public PrometheusException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public PrometheusException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public PrometheusException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public PrometheusException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
