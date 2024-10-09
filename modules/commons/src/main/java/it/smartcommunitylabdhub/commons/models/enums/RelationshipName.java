package it.smartcommunitylabdhub.commons.models.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
	
	@Override
	public String toString() {
		switch (this) {
			case PRODUCEDBY: 
				return "producedBy";
			case CONSUMES:
				return "consumes";
		default:
			return null;
		}
	}

}
