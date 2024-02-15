package it.smartcommunitylabdhub.commons.models.base;

import java.io.Serializable;

/**
 * This baseEntity interface should be implemented by all Entity that need for instance to receive
 * for instance a correct service based on the kind value.
 * <p>
 * es: for kind = etl the Entity will receive the EtlService
 */
//TODO remove implementation of this interface in DTOs!
public interface BaseEntity extends Serializable {}
