package it.smartcommunitylabdhub.commons.models.metadata;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import it.smartcommunitylabdhub.commons.models.base.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@SpecType(kind = "metadata.relationships", entity = EntityName.METADATA)
public final class RelationshipsMetadata extends BaseSpec implements Metadata {
	
	@JsonProperty("relationships")
	private List<RelationshipDetail> relationships = new ArrayList<>();
	
	@Override
	public void configure(Map<String, Serializable> data) {
		RelationshipsMetadata meta = mapper.convertValue(data, RelationshipsMetadata.class);
		this.relationships = meta.getRelationships();
	}
	
	public static RelationshipsMetadata from(Map<String, Serializable> map) {
		RelationshipsMetadata meta = new RelationshipsMetadata();
		meta.configure(map);
		return meta;
	}

}

