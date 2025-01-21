package it.smartcommunitylabdhub.commons.models.metrics;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@JsonSerialize(using = NumberOrNumberArrayJacksonSerializer.class)
@JsonDeserialize(using = NumberOrNumberArrayJacksonDeserializer.class)
public class NumberOrNumberArray implements Serializable {
	
	private Number value;
	
	private List<Number> values;
	
	public NumberOrNumberArray(List<Number> values) {
		this.values = values;
	}
	
	public NumberOrNumberArray(Number value) {
		this.value = value;
	}
	
}
