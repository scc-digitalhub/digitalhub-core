package it.smartcommunitylabdhub.commons.exceptions;

public class CoreRuntimeException extends RuntimeException {

    public CoreRuntimeException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public CoreRuntimeException(String msg) {
        super(msg);
    }
}
