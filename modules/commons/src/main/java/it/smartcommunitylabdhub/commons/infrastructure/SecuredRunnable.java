package it.smartcommunitylabdhub.commons.infrastructure;

import java.util.Collection;

public interface SecuredRunnable {
    void setCredentials(Collection<Credentials> credentials);
}
