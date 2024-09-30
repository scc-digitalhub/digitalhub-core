package it.smartcommunitylabdhub.core.utils;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.core.models.base.BaseEntity;

public class KeyUtils {
    public String getKey(BaseDTO dto, String entityType) {
        StringBuilder sb = new StringBuilder();
        sb.append(Keys.STORE_PREFIX).append(dto.getProject());
        sb.append(Keys.PATH_DIVIDER).append(entityType);
        sb.append(Keys.PATH_DIVIDER).append(dto.getKind());
        sb.append(Keys.PATH_DIVIDER).append(dto.getName());
        if (dto.getId() != null) {
            sb.append(Keys.ID_DIVIDER).append(dto.getId());
        }

        return sb.toString();
    }
    
    public String getKey(BaseEntity entity, String entityType) {
        StringBuilder sb = new StringBuilder();
        sb.append(Keys.STORE_PREFIX).append(entity.getProject());
        sb.append(Keys.PATH_DIVIDER).append(entityType);
        sb.append(Keys.PATH_DIVIDER).append(entity.getKind());
        sb.append(Keys.PATH_DIVIDER).append(entity.getName());
        if (entity.getId() != null) {
            sb.append(Keys.ID_DIVIDER).append(entity.getId());
        }

        return sb.toString();
    }
}
