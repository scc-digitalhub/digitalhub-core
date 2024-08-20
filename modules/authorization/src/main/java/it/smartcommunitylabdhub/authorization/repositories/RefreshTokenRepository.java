package it.smartcommunitylabdhub.authorization.repositories;

import it.smartcommunitylabdhub.authorization.model.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RefreshTokenRepository
    extends JpaRepository<RefreshTokenEntity, String>, JpaSpecificationExecutor<RefreshTokenEntity> {}
