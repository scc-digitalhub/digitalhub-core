package it.smartcommunitylabdhub.commons.infrastructure;

import java.io.Serializable;

public interface RunRunnable extends Serializable {
    String getFramework();

    String getProject();

    String getId();

    void setState(String state);
}
