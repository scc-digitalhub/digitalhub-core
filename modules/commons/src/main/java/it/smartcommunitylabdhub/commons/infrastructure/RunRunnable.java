package it.smartcommunitylabdhub.commons.infrastructure;

import java.io.Serializable;

public interface RunRunnable extends Serializable {
    String getFramework();
    String getTask();
    String getProject();
    String getId();
    String getUser();

    String getState();
    String getMessage();
    String getError();

    void setUser(String user);
    void setState(String state);
    void setMessage(String message);
    void setError(String error);
}
