package it.smartcommunitylabdhub.logs.loki.client;

public class LokiException extends RuntimeException {

    private final int statusCode;

    public LokiException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public LokiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public LokiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public LokiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
