package it.smartcommunitylabdhub.commons.services;

public interface EntityAwareService<D> {
    Class<D> getType();
}
