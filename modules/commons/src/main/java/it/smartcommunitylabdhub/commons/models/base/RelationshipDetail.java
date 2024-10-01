package it.smartcommunitylabdhub.commons.models.base;

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import it.smartcommunitylabdhub.commons.models.enums.RelationshipName;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelationshipDetail {
	@NotNull
	private RelationshipName type;
	@Nullable
	private String source;
	@Nullable
	private String dest;
	
}
