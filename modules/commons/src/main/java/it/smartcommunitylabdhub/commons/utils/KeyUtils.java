package it.smartcommunitylabdhub.commons.utils;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;

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
    
}
