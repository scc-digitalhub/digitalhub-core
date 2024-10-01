package it.smartcommunitylabdhub.commons.models.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.ToString;

@ToString
public enum RelationshipName {
	PRODUCEDBY("producedBy"),
    CONSUMES("consumes");
	
	private final String value;
	
	RelationshipName(String value) {
		this.value = value;
	}
	
	@JsonCreator
	public static RelationshipName from(String value) {
		for(RelationshipName rel : RelationshipName.values()) {
			if(rel.value.equalsIgnoreCase(value))
				return rel;
		}
		return null;
	}
	
	@JsonValue
	public String getValue()  { 
		return this.value;
	}

}
