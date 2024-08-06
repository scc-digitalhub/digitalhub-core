package it.smartcommunitylabdhub.core.models.builders.files;

<<<<<<< HEAD
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

=======
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
>>>>>>> origin/file_info_repo
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.entities.files.FilesInfo;
import it.smartcommunitylabdhub.core.models.entities.FilesInfoEntity;
import lombok.extern.slf4j.Slf4j;
<<<<<<< HEAD
=======
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
>>>>>>> origin/file_info_repo

@Component
@Slf4j
public class FilesInfoEntityBuilder implements Converter<FilesInfo, FilesInfoEntity> {
<<<<<<< HEAD
	private final ObjectMapper mapper = JacksonMapper.CBOR_OBJECT_MAPPER;
	
	public FilesInfoEntity build(FilesInfo dto) {
		byte[] value = null;
		try {
			if(dto.getFiles() != null) {
				value = mapper.writeValueAsBytes(dto.getFiles());
			}
		} catch (JsonProcessingException e) {
			log.warn("FilesInfoEntity build error: {}", e.getMessage());
		}
		return FilesInfoEntity.builder()
				.id(dto.getId())
				.entityName(dto.getEntityName())
				.entityId(dto.getEntityId())
				.files(value)
				.build();
	}
	
	@Override
	public FilesInfoEntity convert(FilesInfo source) {
		return build(source);
	}

=======

    private final ObjectMapper mapper = JacksonMapper.CBOR_OBJECT_MAPPER;

    public FilesInfoEntity build(FilesInfo dto) {
        byte[] value = null;
        try {
            if (dto.getFiles() != null) {
                value = mapper.writeValueAsBytes(dto.getFiles());
            }
        } catch (JsonProcessingException e) {
            log.error("FilesInfoEntity build error: {}", e.getMessage());
        }

        return FilesInfoEntity
            .builder()
            .id(dto.getId())
            .entityName(dto.getEntityName())
            .entityId(dto.getEntityId())
            .files(value)
            .build();
    }

    @Override
    public FilesInfoEntity convert(FilesInfo source) {
        return build(source);
    }
>>>>>>> origin/file_info_repo
}
