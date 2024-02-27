package it.smartcommunitylabdhub.commons.services;

import it.smartcommunitylabdhub.commons.models.entities.dataitem.DataItem;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DataItemService {
    Page<DataItem> getDataItems(Map<String, String> filter, Pageable pageable);

    DataItem createDataItem(DataItem dataItemDTO);

    DataItem getDataItem(String uuid);

    DataItem updateDataItem(DataItem dataItemDTO, String uuid);

    boolean deleteDataItem(String uuid);
}
