package it.smartcommunitylabdhub.core.models.converters.types;

import it.smartcommunitylabdhub.commons.models.enums.RelationshipName;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RelationshipStringAttributeConverter implements AttributeConverter<RelationshipName, String> {

    @Override
    public String convertToDatabaseColumn(RelationshipName attribute) {
        if (attribute == null) {
            return null;
        }

        return attribute.name();
    }

    @Override
    public RelationshipName convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        //enum is uppercase
        return RelationshipName.valueOf(dbData);
    }
}
